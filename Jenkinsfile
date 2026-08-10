pipeline {
    agent any

    triggers {
        githubPush()
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    environment {
        JAVA_HOME    = 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot'
        ORAS_HOME    = 'C:\\tools\\oras'
        PATH         = "${JAVA_HOME}\\bin;${env.PATH}"
        BACKEND_ACR  = 'naukribackendacr7291.azurecr.io'
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
    }

    stages {

        stage('1. Checkout') {
            steps {
                echo '===== CHECKOUT SOURCE CODE ====='

                git branch: 'main',
                    url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git'
            }
        }

        stage('2. Preflight checks') {
            steps {
                script {
                    def tools = [
                        'java -version',
                        'mvn -version',
                        'node -v',
                        'npm -v',
                        'oras version',
                        'az version'
                    ]

                    tools.each { cmd ->
                        def status = bat(
                            script: "@${cmd}",
                            returnStatus: true
                        )

                        if (status != 0) {
                            error "Preflight failed: '${cmd}' is not available on this agent."
                        }
                    }
                }
            }
        }

        stage('3. Build Backend') {
            steps {
                bat '''
                    mvn -f backend\\pom.xml clean package -DskipTests -Dmaven.test.skip=true
                '''
            }

            post {
                success {
                    script {
                        if (!fileExists('backend/target/naukri-be.jar')) {
                            error 'Backend JAR was not generated.'
                        }
                    }
                }
            }
        }

        stage('4. Build Frontend') {
            steps {
                dir('frontend') {
                    bat 'npm ci'
                    bat 'npm run build'
                }
            }

            post {
                success {
                    script {
                        if (!fileExists('frontend/dist/index.html')) {
                            error 'Frontend build was not generated.'
                        }
                    }
                }
            }
        }

        stage('5. Build Electron Application') {
            steps {
                powershell '''
                    & "$env:WORKSPACE\\build\\build.ps1" -Variant Ship

                    if ($LASTEXITCODE -ne 0) {
                        exit $LASTEXITCODE
                    }
                '''
            }
        }

        stage('6. Verify EXE') {
            steps {
                powershell '''
                    $dist = "$env:WORKSPACE\\dist"

                    if (-not (Test-Path $dist)) {
                        throw "dist directory does not exist."
                    }

                    $exeFiles = Get-ChildItem $dist -Filter "*.exe" -File

                    if ($exeFiles.Count -eq 0) {
                        throw "No EXE artifacts found."
                    }

                    Write-Host "===== EXE FILES ====="

                    foreach ($exe in $exeFiles) {
                        Write-Host $exe.FullName
                        Write-Host "Size: $($exe.Length) bytes"
                    }
                '''
            }
        }

        stage('7. Azure Login') {
            steps {
                withCredentials([
                    azureServicePrincipal(
                        credentialsId: 'azure-sp-naukri',
                        subscriptionIdVariable: 'AZ_SUBSCRIPTION_ID',
                        clientIdVariable: 'AZ_CLIENT_ID',
                        clientSecretVariable: 'AZ_CLIENT_SECRET',
                        tenantIdVariable: 'AZ_TENANT_ID'
                    )
                ]) {
                    bat '''
                        az login --service-principal ^
                          -u %AZ_CLIENT_ID% ^
                          -p %AZ_CLIENT_SECRET% ^
                          --tenant %AZ_TENANT_ID%

                        az account set --subscription %AZ_SUBSCRIPTION_ID%
                    '''
                }
            }
        }

        stage('8. ORAS Login to ACR') {
            steps {
                bat '''
                    for /f "usebackq tokens=*" %%t in (`az acr login --name naukribackendacr7291 --expose-token --output tsv --query accessToken`) do set BACKEND_TOKEN=%%t

                    oras login naukribackendacr7291.azurecr.io ^
                        -u 00000000-0000-0000-0000-000000000000 ^
                        -p %BACKEND_TOKEN%
                '''
            }
        }

        stage('9. Push Backend to ACR') {
            steps {
                bat "oras push ${BACKEND_ACR}/backend:${IMAGE_TAG} backend/target/naukri-be.jar"
                bat "oras push ${BACKEND_ACR}/backend:latest backend/target/naukri-be.jar"
            }
        }

        stage('10. Archive EXE') {
            steps {
                archiveArtifacts(
                    artifacts: 'dist/*.exe',
                    fingerprint: true
                )
            }
        }

        stage('11. Upload EXE to Azure Blob') {
            steps {
                azureUpload(
                    containerName: 'smcont',
                    storageType: 'blobstorage',
                    filesPath: 'dist/*.exe',
                    storageCredentialId: 'azure-storage-cred'
                )
            }
        }
    }

    post {
        success {
            echo "Build ${env.BUILD_NUMBER} succeeded. EXE generated, archived, uploaded to Blob, and backend pushed to ACR."
        }

        failure {
            echo "Build ${env.BUILD_NUMBER} failed - check the first red stage above."
        }

        always {
            bat 'az logout || exit 0'
            cleanWs()
        }
    }
}
