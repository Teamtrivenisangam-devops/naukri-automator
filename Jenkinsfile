pipeline {

    agent any

    triggers {
        githubPush()
    }

    options {
        timestamps()
        disableConcurrentBuilds()

        timeout(
            time: 30,
            unit: 'MINUTES'
        )

        buildDiscarder(
            logRotator(
                numToKeepStr: '15',
                artifactNumToKeepStr: '15'
            )
        )
    }

    environment {

        // =====================================================
        // JAVA
        // =====================================================

        JAVA_HOME =
            'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot'

        PATH =
            "${JAVA_HOME}\\bin;${env.PATH}"


        // =====================================================
        // AZURE BACKEND STORAGE
        // =====================================================

        AZ_BACKEND_STORAGE =
            'naukribackend7291'

        AZ_BACKEND_CONTAINER =
            'backend'


        // =====================================================
        // AZURE FRONTEND STORAGE
        // =====================================================

        AZ_FRONTEND_STORAGE =
            'naukrifrontend7291'

        AZ_FRONTEND_CONTAINER =
            'frontend'


        // =====================================================
        // VERSION VARIABLES
        // =====================================================

        BASE_VERSION = ''

        RELEASE_VERSION = ''
    }


    stages {

        // =====================================================
        // 1. CHECKOUT
        // =====================================================

        stage('Checkout') {

            steps {

                git(
                    branch: 'main',

                    url:
                        'https://github.com/Teamtrivenisangam-devops/naukri-automator.git',

                    credentialsId:
                        'github-credentials'
                )
            }
        }


        // =====================================================
        // 2. SKIP CI CHECK
        // =====================================================

        stage('Skip CI Check') {

            steps {

                script {

                    def lastMsg =
                        bat(
                            script:
                                '@git log -1 --pretty=%%B',

                            returnStdout: true
                        )
                        .trim()


                    echo "Latest commit message:"
                    echo lastMsg


                    if (lastMsg.contains('[skip ci]')) {

                        currentBuild.result =
                            'NOT_BUILT'

                        error(
                            "Skipping build because commit contains [skip ci]"
                        )
                    }
                }
            }
        }


        // =====================================================
        // 3. PREFLIGHT
        // =====================================================

        stage('Preflight') {

            steps {

                bat '''
                    echo ==========================================
                    echo PREFLIGHT CHECK
                    echo ==========================================

                    echo.
                    echo Java Version
                    java -version

                    echo.
                    echo Maven Version
                    mvn -version

                    echo.
                    echo Node Version
                    node -v

                    echo.
                    echo npm Version
                    npm -v

                    echo.
                    echo Azure CLI Version
                    az version
                '''
            }
        }


        // =====================================================
        // 4. DETERMINE BASE VERSION
        // =====================================================
        //
        // Example:
        //
        // pom.xml
        // 0.0.1-SNAPSHOT
        //
        // Base     = 0.0.1
        // Release  = 0.0.2
        //
        // =====================================================

        stage('Determine Base Version') {

            steps {

                script {

                    def pomVersion =
                        bat(
                            script:
                                '''
                                @mvn -f backend\\pom.xml help:evaluate ^
                                -Dexpression=project.version ^
                                -q -B -DforceStdout
                                ''',

                            returnStdout: true
                        )
                        .trim()


                    echo "Maven POM Version: ${pomVersion}"


                    def baseVersion =
                        pomVersion.replace(
                            '-SNAPSHOT',
                            ''
                        )


                    def parts =
                        baseVersion.tokenize('.')


                    if (parts.size() != 3) {

                        error(
                            "Invalid Maven version: ${pomVersion}. " +
                            "Expected format: major.minor.patch"
                        )
                    }


                    def major =
                        parts[0].toInteger()

                    def minor =
                        parts[1].toInteger()

                    def patch =
                        parts[2].toInteger()


                    def releaseVersion =
                        "${major}.${minor}.${patch + 1}"


                    env.BASE_VERSION =
                        baseVersion


                    env.RELEASE_VERSION =
                        releaseVersion


                    echo "=========================================="
                    echo "Base Version    : ${env.BASE_VERSION}"
                    echo "Release Version : ${env.RELEASE_VERSION}"
                    echo "Jenkins Build   : ${env.BUILD_NUMBER}"
                    echo "=========================================="
                }
            }
        }


        // =====================================================
        // 5. SET RELEASE VERSION
        // =====================================================
        //
        // Example:
        //
        // 0.0.1-SNAPSHOT
        //
        // becomes temporarily:
        //
        // 0.0.2
        //
        // This makes the generated JAR version 0.0.2.
        //
        // =====================================================

        stage('Set Release Version') {

            steps {

                bat '''
                    echo ==========================================
                    echo SETTING RELEASE VERSION
                    echo ==========================================

                    mvn -f backend\\pom.xml versions:set ^
                        -DnewVersion=%RELEASE_VERSION% ^
                        -DgenerateBackupPoms=false

                    echo.
                    echo Verifying version...

                    mvn -f backend\\pom.xml help:evaluate ^
                        -Dexpression=project.version ^
                        -q -B -DforceStdout
                '''
            }
        }


        // =====================================================
        // 6. BUILD BACKEND
        // =====================================================

        stage('Build Backend') {

            steps {

                bat '''
                    echo ==========================================
                    echo BUILD BACKEND
                    echo ==========================================

                    mvn -f backend\\pom.xml clean package ^
                        -DskipTests ^
                        -Dmaven.test.skip=true
                '''
            }
        }


        // =====================================================
        // 7. VERIFY BACKEND
        // =====================================================

        stage('Verify Backend Artifact') {

            steps {

                powershell '''

                    $jarPath =
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar"


                    if (-not (Test-Path $jarPath)) {

                        throw "Backend JAR not found: $jarPath"
                    }


                    Write-Host "Backend artifact found:"
                    Write-Host $jarPath
                '''
            }
        }


        // =====================================================
        // 8. BUILD FRONTEND
        // =====================================================

        stage('Build Frontend') {

            steps {

                dir('frontend') {

                    bat '''
                        echo ==========================================
                        echo BUILD FRONTEND
                        echo ==========================================

                        npm ci

                        npm run build
                    '''
                }
            }
        }


        // =====================================================
        // 9. BUILD ELECTRON
        // =====================================================

        stage('Build Electron') {

            steps {

                powershell '''

                    Write-Host "=========================================="
                    Write-Host "BUILD ELECTRON"
                    Write-Host "=========================================="


                    $global:LASTEXITCODE = 0


                    & "$env:WORKSPACE\\build\\build.ps1" -Variant Ship


                    if ($LASTEXITCODE -ne 0) {

                        exit $LASTEXITCODE
                    }


                    $exeFiles =
                        Get-ChildItem `
                        "$env:WORKSPACE\\dist" `
                        -Filter "*.exe" `
                        -File


                    if ($exeFiles.Count -eq 0) {

                        throw "Electron EXE was not generated"
                    }


                    Write-Host "Electron artifacts:"


                    $exeFiles |
                        ForEach-Object {

                            Write-Host $_.FullName
                        }
                '''
            }
        }


        // =====================================================
        // 10. PREPARE ARTIFACTS
        // =====================================================

        stage('Prepare Artifacts') {

            steps {

                powershell '''

                    $releaseDir =
                        "$env:WORKSPACE\\release-artifacts"


                    $backendDir =
                        "$releaseDir\\backend"


                    $frontendDir =
                        "$releaseDir\\frontend"


                    # ------------------------------------------
                    # CLEAN
                    # ------------------------------------------

                    if (Test-Path $releaseDir) {

                        Remove-Item `
                            $releaseDir `
                            -Recurse `
                            -Force
                    }


                    # ------------------------------------------
                    # CREATE DIRECTORIES
                    # ------------------------------------------

                    New-Item `
                        -ItemType Directory `
                        -Path $backendDir `
                        -Force |
                        Out-Null


                    New-Item `
                        -ItemType Directory `
                        -Path $frontendDir `
                        -Force |
                        Out-Null


                    # ==========================================
                    # BACKEND JAR
                    # ==========================================

                    $jarPath =
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar"


                    $backendArtifact =
                        "$backendDir\\naukri-be-$env:RELEASE_VERSION.jar"


                    Copy-Item `
                        $jarPath `
                        $backendArtifact `
                        -Force


                    # ==========================================
                    # REACT FRONTEND
                    # ==========================================

                    $frontendBuild =
                        "$env:WORKSPACE\\frontend\\dist"


                    if (Test-Path $frontendBuild) {

                        New-Item `
                            -ItemType Directory `
                            -Path "$frontendDir\\web" `
                            -Force |
                            Out-Null


                        Copy-Item `
                            "$frontendBuild\\*" `
                            "$frontendDir\\web" `
                            -Recurse `
                            -Force
                    }


                    # ==========================================
                    # ELECTRON EXE
                    # ==========================================

                    $exeFiles =
                        Get-ChildItem `
                        "$env:WORKSPACE\\dist" `
                        -Filter "*.exe" `
                        -File


                    New-Item `
                        -ItemType Directory `
                        -Path "$frontendDir\\electron" `
                        -Force |
                        Out-Null


                    $exeFiles |
                        ForEach-Object {

                            $newName =
                                [System.IO.Path]::GetFileNameWithoutExtension(
                                    $_.Name
                                ) +
                                "-$env:RELEASE_VERSION.exe"


                            Copy-Item `
                                $_.FullName `
                                "$frontendDir\\electron\\$newName" `
                                -Force
                        }


                    # ==========================================
                    # DISPLAY ARTIFACTS
                    # ==========================================

                    Write-Host ""
                    Write-Host "=========================================="
                    Write-Host "PREPARED ARTIFACTS"
                    Write-Host "=========================================="


                    Get-ChildItem `
                        $releaseDir `
                        -Recurse |
                        Select-Object FullName, Length
                '''
            }
        }


        // =====================================================
        // 11. AZURE LOGIN
        // =====================================================

        stage('Azure Login') {

            steps {

                withCredentials([

                    azureServicePrincipal(

                        credentialsId:
                            'azure-sp-naukri',

                        subscriptionIdVariable:
                            'AZ_SUBSCRIPTION_ID',

                        clientIdVariable:
                            'AZ_CLIENT_ID',

                        clientSecretVariable:
                            'AZ_CLIENT_SECRET',

                        tenantIdVariable:
                            'AZ_TENANT_ID'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo AZURE LOGIN
                        echo ==========================================


                        az login ^
                            --service-principal ^
                            -u %AZ_CLIENT_ID% ^
                            -p %AZ_CLIENT_SECRET% ^
                            --tenant %AZ_TENANT_ID% ^
                            1>NUL


                        az account set ^
                            --subscription %AZ_SUBSCRIPTION_ID%


                        echo Azure login successful.
                    '''
                }
            }
        }


        // =====================================================
        // 12. VERIFY STORAGE ACCESS
        // =====================================================

        stage('Verify Azure Storage Access') {

            steps {

                withCredentials([

                    azureServicePrincipal(

                        credentialsId:
                            'azure-sp-naukri',

                        subscriptionIdVariable:
                            'AZ_SUBSCRIPTION_ID',

                        clientIdVariable:
                            'AZ_CLIENT_ID',

                        clientSecretVariable:
                            'AZ_CLIENT_SECRET',

                        tenantIdVariable:
                            'AZ_TENANT_ID'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo VERIFYING BACKEND STORAGE
                        echo ==========================================


                        az storage container show ^
                            --name %AZ_BACKEND_CONTAINER% ^
                            --account-name %AZ_BACKEND_STORAGE% ^
                            --auth-mode login ^
                            -o table


                        echo ==========================================
                        echo VERIFYING FRONTEND STORAGE
                        echo ==========================================


                        az storage container show ^
                            --name %AZ_FRONTEND_CONTAINER% ^
                            --account-name %AZ_FRONTEND_STORAGE% ^
                            --auth-mode login ^
                            -o table
                    '''
                }
            }
        }


        // =====================================================
        // 13. UPLOAD BACKEND
        // =====================================================

        stage('Upload Backend') {

            steps {

                withCredentials([

                    azureServicePrincipal(

                        credentialsId:
                            'azure-sp-naukri',

                        subscriptionIdVariable:
                            'AZ_SUBSCRIPTION_ID',

                        clientIdVariable:
                            'AZ_CLIENT_ID',

                        clientSecretVariable:
                            'AZ_CLIENT_SECRET',

                        tenantIdVariable:
                            'AZ_TENANT_ID'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo UPLOADING BACKEND
                        echo ==========================================


                        az storage blob upload-batch ^
                            --account-name %AZ_BACKEND_STORAGE% ^
                            --destination %AZ_BACKEND_CONTAINER% ^
                            --source release-artifacts\\backend ^
                            --destination-path %RELEASE_VERSION% ^
                            --auth-mode login


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        echo Backend upload successful.
                    '''
                }
            }
        }


        // =====================================================
        // 14. UPLOAD FRONTEND
        // =====================================================

        stage('Upload Frontend') {

            steps {

                withCredentials([

                    azureServicePrincipal(

                        credentialsId:
                            'azure-sp-naukri',

                        subscriptionIdVariable:
                            'AZ_SUBSCRIPTION_ID',

                        clientIdVariable:
                            'AZ_CLIENT_ID',

                        clientSecretVariable:
                            'AZ_CLIENT_SECRET',

                        tenantIdVariable:
                            'AZ_TENANT_ID'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo UPLOADING FRONTEND
                        echo ==========================================


                        az storage blob upload-batch ^
                            --account-name %AZ_FRONTEND_STORAGE% ^
                            --destination %AZ_FRONTEND_CONTAINER% ^
                            --source release-artifacts\\frontend ^
                            --destination-path %RELEASE_VERSION% ^
                            --auth-mode login


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        echo Frontend upload successful.
                    '''
                }
            }
        }


        // =====================================================
        // 15. VERIFY UPLOAD
        // =====================================================

        stage('Verify Upload') {

            steps {

                withCredentials([

                    azureServicePrincipal(

                        credentialsId:
                            'azure-sp-naukri',

                        subscriptionIdVariable:
                            'AZ_SUBSCRIPTION_ID',

                        clientIdVariable:
                            'AZ_CLIENT_ID',

                        clientSecretVariable:
                            'AZ_CLIENT_SECRET',

                        tenantIdVariable:
                            'AZ_TENANT_ID'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo BACKEND BLOBS
                        echo ==========================================


                        az storage blob list ^
                            --account-name %AZ_BACKEND_STORAGE% ^
                            --container-name %AZ_BACKEND_CONTAINER% ^
                            --prefix %RELEASE_VERSION% ^
                            --auth-mode login ^
                            -o table


                        echo ==========================================
                        echo FRONTEND BLOBS
                        echo ==========================================


                        az storage blob list ^
                            --account-name %AZ_FRONTEND_STORAGE% ^
                            --container-name %AZ_FRONTEND_CONTAINER% ^
                            --prefix %RELEASE_VERSION% ^
                            --auth-mode login ^
                            -o table
                    '''
                }
            }
        }


        // =====================================================
        // 16. CREATE RELEASE TAG
        // =====================================================
        //
        // At this point:
        //
        // Build       SUCCESS
        // Backend     uploaded
        // Frontend    uploaded
        //
        // Create:
        //
        // v0.0.2
        //
        // =====================================================

        stage('Tag Release') {

            steps {

                withCredentials([

                    gitUsernamePassword(

                        credentialsId:
                            'github-credentials',

                        gitToolName:
                            'Default'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo CREATING RELEASE TAG
                        echo ==========================================


                        git config user.name "Jenkins CI"

                        git config user.email "jenkins@company.com"


                        git tag -a ^
                            v%RELEASE_VERSION% ^
                            -m "Release v%RELEASE_VERSION% [skip ci]"


                        git push origin ^
                            v%RELEASE_VERSION%
                    '''
                }
            }
        }


        // =====================================================
        // 17. PREPARE NEXT SNAPSHOT
        // =====================================================
        //
        // Example:
        //
        // Current:
        // 0.0.1-SNAPSHOT
        //
        // Release:
        // 0.0.2
        //
        // Next:
        // 0.0.2-SNAPSHOT
        //
        // =====================================================

        stage('Prepare Next Snapshot') {

            steps {

                withCredentials([

                    gitUsernamePassword(

                        credentialsId:
                            'github-credentials',

                        gitToolName:
                            'Default'
                    )

                ]) {

                    bat '''

                        echo ==========================================
                        echo PREPARING NEXT MAVEN VERSION
                        echo ==========================================


                        mvn -f backend\\pom.xml versions:set ^
                            -DnewVersion=%RELEASE_VERSION%-SNAPSHOT ^
                            -DgenerateBackupPoms=false


                        echo.
                        echo New Maven version:

                        mvn -f backend\\pom.xml help:evaluate ^
                            -Dexpression=project.version ^
                            -q -B -DforceStdout


                        git config user.name "Jenkins CI"

                        git config user.email "jenkins@company.com"


                        git add backend\\pom.xml


                        git commit ^
                            -m "chore: prepare next development version %RELEASE_VERSION%-SNAPSHOT [skip ci]"


                        git push origin main
                    '''
                }
            }
        }


        // =====================================================
        // 18. ARCHIVE JENKINS ARTIFACTS
        // =====================================================

        stage('Archive') {

            steps {

                archiveArtifacts(
                    artifacts:
                        'release-artifacts/**/*',

                    fingerprint:
                        true
                )
            }
        }
    }


    // =========================================================
    // POST
    // =========================================================

    post {

        always {

            bat '''
                az logout || exit 0
            '''

            cleanWs()
        }


        success {

            echo """
            ==========================================
                 RELEASE SUCCESSFUL
            ==========================================

            Jenkins Build:
            ${env.BUILD_NUMBER}

            Base Version:
            ${env.BASE_VERSION}

            Release Version:
            ${env.RELEASE_VERSION}

            Backend Storage:
            ${env.AZ_BACKEND_STORAGE}

            Backend Container:
            ${env.AZ_BACKEND_CONTAINER}

            Backend Path:
            ${env.RELEASE_VERSION}/

            Frontend Storage:
            ${env.AZ_FRONTEND_STORAGE}

            Frontend Container:
            ${env.AZ_FRONTEND_CONTAINER}

            Frontend Path:
            ${env.RELEASE_VERSION}/

            Git Tag:
            v${env.RELEASE_VERSION}

            ==========================================
            """
        }


        failure {

            echo """
            ==========================================
                    RELEASE FAILED
            ==========================================

            Jenkins Build:
            ${env.BUILD_NUMBER}

            Base Version:
            ${env.BASE_VERSION}

            Candidate Version:
            ${env.RELEASE_VERSION}

            The Maven version will NOT be advanced
            because the pipeline failed.

            ==========================================
            """
        }


        unstable {

            echo """
            ==========================================
                    BUILD UNSTABLE
            ==========================================

            Build URL:
            ${env.BUILD_URL}

            ==========================================
            """
        }
    }
}
