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
        // AZURE STORAGE ACCOUNT
        // =====================================================

        AZ_STORAGE_ACCOUNT =
            'naukristorage7291'


        // =====================================================
        // AZURE CONTAINERS
        // =====================================================

        AZ_BACKEND_CONTAINER =
            'naukribackend7291'

        AZ_FRONTEND_CONTAINER =
            'naukrifrontend7291'


        // =====================================================
        // VERSION
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
        // 2. SKIP CI
        // =====================================================

        stage('Skip CI Check') {

            steps {

                script {

                    def lastMsg = bat(
                        script:
                            '@git log -1 --pretty=%%B',
                        returnStdout: true
                    ).trim()

                    echo "Latest commit:"
                    echo lastMsg

                    if (lastMsg.contains('[skip ci]')) {

                        currentBuild.result = 'NOT_BUILT'

                        error(
                            'Skipping build because commit contains [skip ci]'
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
                    echo PREFLIGHT
                    echo ==========================================

                    java -version
                    mvn -version
                    node -v
                    npm -v
                    az version
                '''
            }
        }


        // =====================================================
        // 4. DETERMINE VERSION
        // =====================================================

        stage('Determine Release Version') {

            steps {

                script {

                    def pomVersion = bat(
                        script: '''
                            @mvn -f backend\\pom.xml help:evaluate ^
                            -Dexpression=project.version ^
                            -q -B -DforceStdout
                        ''',
                        returnStdout: true
                    ).trim()


                    echo "Maven POM version = [${pomVersion}]"


                    // Remove SNAPSHOT

                    def baseVersion =
                        pomVersion
                            .replace('-SNAPSHOT', '')
                            .trim()


                    def parts =
                        baseVersion.tokenize('.')


                    if (parts.size() != 3) {

                        error(
                            "Invalid version '${pomVersion}'. " +
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
                    echo "Maven Version   : ${pomVersion}"
                    echo "Base Version    : ${env.BASE_VERSION}"
                    echo "Release Version : ${env.RELEASE_VERSION}"
                    echo "Build Number    : ${env.BUILD_NUMBER}"
                    echo "=========================================="
                }
            }
        }


        // =====================================================
        // 5. VERIFY VERSION
        // =====================================================

        stage('Verify Release Version') {

            steps {

                script {

                    if (!env.RELEASE_VERSION?.trim()) {

                        error(
                            'RELEASE_VERSION is empty. Stopping pipeline.'
                        )
                    }

                    echo "Release version confirmed: ${env.RELEASE_VERSION}"
                }
            }
        }


        // =====================================================
        // 6. SET RELEASE VERSION
        // =====================================================

        stage('Set Release Version') {

            steps {

                bat """
                    echo Setting Maven version to ${env.RELEASE_VERSION}

                    mvn -f backend\\pom.xml versions:set ^
                        -DnewVersion=${env.RELEASE_VERSION} ^
                        -DgenerateBackupPoms=false
                """
            }
        }


        // =====================================================
        // 7. BUILD BACKEND
        // =====================================================

        stage('Build Backend') {

            steps {

                bat '''
                    echo ==========================================
                    echo BUILDING BACKEND
                    echo ==========================================

                    mvn -f backend\\pom.xml clean package ^
                        -DskipTests ^
                        -Dmaven.test.skip=true
                '''
            }
        }


        // =====================================================
        // 8. VERIFY BACKEND
        // =====================================================

        stage('Verify Backend Artifact') {

            steps {

                powershell '''

                    $jar =
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar"


                    if (-not (Test-Path $jar)) {

                        throw "Backend JAR not found: $jar"
                    }


                    Write-Host "Backend JAR found:"
                    Write-Host $jar
                '''
            }
        }


        // =====================================================
        // 9. BUILD FRONTEND
        // =====================================================

        stage('Build Frontend') {

            steps {

                dir('frontend') {

                    bat '''
                        echo ==========================================
                        echo BUILDING FRONTEND
                        echo ==========================================

                        npm ci

                        npm run build
                    '''
                }
            }
        }


        // =====================================================
        // 10. BUILD ELECTRON
        // =====================================================

        stage('Build Electron') {

            steps {

                powershell '''

                    Write-Host "=========================================="
                    Write-Host "BUILDING ELECTRON"
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

                        throw "Electron EXE not found"
                    }


                    Write-Host "Electron build successful."
                '''
            }
        }


        // =====================================================
        // 11. PREPARE ARTIFACTS
        // =====================================================

        stage('Prepare Artifacts') {

            steps {

                powershell """

                    \$releaseDir =
                        "\$env:WORKSPACE\\release-artifacts"

                    \$backendDir =
                        "\$releaseDir\\backend"

                    \$frontendDir =
                        "\$releaseDir\\frontend"


                    if (Test-Path \$releaseDir) {

                        Remove-Item `
                            \$releaseDir `
                            -Recurse `
                            -Force
                    }


                    New-Item `
                        -ItemType Directory `
                        -Path \$backendDir `
                        -Force |
                        Out-Null


                    New-Item `
                        -ItemType Directory `
                        -Path \$frontendDir `
                        -Force |
                        Out-Null


                    # ==========================================
                    # BACKEND
                    # ==========================================

                    \$jar =
                        "\$env:WORKSPACE\\backend\\target\\naukri-be.jar"


                    Copy-Item `
                        \$jar `
                        "\$backendDir\\naukri-be-${env.RELEASE_VERSION}.jar" `
                        -Force


                    # ==========================================
                    # FRONTEND WEB
                    # ==========================================

                    \$frontendBuild =
                        "\$env:WORKSPACE\\frontend\\dist"


                    if (-not (Test-Path \$frontendBuild)) {

                        throw "Frontend dist directory not found"
                    }


                    New-Item `
                        -ItemType Directory `
                        -Path "\$frontendDir\\web" `
                        -Force |
                        Out-Null


                    Copy-Item `
                        "\$frontendBuild\\*" `
                        "\$frontendDir\\web" `
                        -Recurse `
                        -Force


                    # ==========================================
                    # ELECTRON
                    # ==========================================

                    \$electronDir =
                        "\$frontendDir\\electron"


                    New-Item `
                        -ItemType Directory `
                        -Path \$electronDir `
                        -Force |
                        Out-Null


                    \$exeFiles =
                        Get-ChildItem `
                        "\$env:WORKSPACE\\dist" `
                        -Filter "*.exe" `
                        -File


                    foreach (\$exe in \$exeFiles) {

                        \$newName =
                            [System.IO.Path]::GetFileNameWithoutExtension(
                                \$exe.Name
                            ) +
                            "-${env.RELEASE_VERSION}.exe"


                        Copy-Item `
                            \$exe.FullName `
                            "\$electronDir\\\$newName" `
                            -Force
                    }


                    Write-Host "=========================================="
                    Write-Host "ARTIFACTS READY"
                    Write-Host "=========================================="


                    Get-ChildItem `
                        \$releaseDir `
                        -Recurse
                """
            }
        }


        // =====================================================
        // 12. AZURE LOGIN
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


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        az account set ^
                            --subscription %AZ_SUBSCRIPTION_ID%


                        echo Azure login successful.
                    '''
                }
            }
        }


        // =====================================================
        // 13. VERIFY STORAGE
        // =====================================================

        stage('Verify Azure Storage') {

            steps {

                bat '''

                    echo ==========================================
                    echo VERIFYING BACKEND CONTAINER
                    echo ==========================================


                    az storage container show ^
                        --account-name "%AZ_STORAGE_ACCOUNT%" ^
                        --name "%AZ_BACKEND_CONTAINER%" ^
                        --auth-mode login ^
                        -o table


                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                    echo ==========================================
                    echo VERIFYING FRONTEND CONTAINER
                    echo ==========================================


                    az storage container show ^
                        --account-name "%AZ_STORAGE_ACCOUNT%" ^
                        --name "%AZ_FRONTEND_CONTAINER%" ^
                        --auth-mode login ^
                        -o table


                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                    echo ==========================================
                    echo STORAGE ACCESS SUCCESSFUL
                    echo ==========================================
                '''
            }
        }


        // =====================================================
        // 14. UPLOAD BACKEND
        // =====================================================

        stage('Upload Backend') {

            steps {

                /*
                 * IMPORTANT:
                 * Groovy inserts RELEASE_VERSION directly.
                 * This avoids the previous empty %RELEASE_VERSION%
                 * problem.
                 */

                bat """
                    echo ==========================================
                    echo UPLOADING BACKEND
                    echo ==========================================

                    echo Storage Account : ${env.AZ_STORAGE_ACCOUNT}
                    echo Container       : ${env.AZ_BACKEND_CONTAINER}
                    echo Version         : ${env.RELEASE_VERSION}

                    az storage blob upload-batch ^
                        --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                        --destination "${env.AZ_BACKEND_CONTAINER}" ^
                        --source "release-artifacts\\backend" ^
                        --destination-path "${env.RELEASE_VERSION}" ^
                        --auth-mode login

                    if %ERRORLEVEL% NEQ 0 (
                        echo Backend upload FAILED
                        exit /b %ERRORLEVEL%
                    )

                    echo ==========================================
                    echo BACKEND UPLOAD SUCCESSFUL
                    echo ==========================================
                """
            }
        }


        // =====================================================
        // 15. UPLOAD FRONTEND
        // =====================================================

        stage('Upload Frontend') {

            steps {

                bat """

                    echo ==========================================
                    echo UPLOADING FRONTEND
                    echo ==========================================

                    echo Storage Account : ${env.AZ_STORAGE_ACCOUNT}
                    echo Container       : ${env.AZ_FRONTEND_CONTAINER}
                    echo Version         : ${env.RELEASE_VERSION}


                    az storage blob upload-batch ^
                        --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                        --destination "${env.AZ_FRONTEND_CONTAINER}" ^
                        --source "release-artifacts\\frontend" ^
                        --destination-path "${env.RELEASE_VERSION}" ^
                        --auth-mode login


                    if %ERRORLEVEL% NEQ 0 (
                        echo Frontend upload FAILED
                        exit /b %ERRORLEVEL%
                    )


                    echo ==========================================
                    echo FRONTEND UPLOAD SUCCESSFUL
                    echo ==========================================
                """
            }
        }


        // =====================================================
        // 16. VERIFY UPLOAD
        // =====================================================

        stage('Verify Upload') {

            steps {

                bat """

                    echo ==========================================
                    echo VERIFYING UPLOAD
                    echo ==========================================


                    echo BACKEND:
                    az storage blob list ^
                        --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                        --container-name "${env.AZ_BACKEND_CONTAINER}" ^
                        --prefix "${env.RELEASE_VERSION}/" ^
                        --auth-mode login ^
                        -o table


                    echo FRONTEND:
                    az storage blob list ^
                        --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                        --container-name "${env.AZ_FRONTEND_CONTAINER}" ^
                        --prefix "${env.RELEASE_VERSION}/" ^
                        --auth-mode login ^
                        -o table
                """
            }
        }


        // =====================================================
        // 17. CREATE GIT TAG
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

                    bat """

                        echo ==========================================
                        echo CREATING RELEASE TAG
                        echo ==========================================


                        git config user.name "Jenkins CI"

                        git config user.email "jenkins@company.com"


                        git tag -a ^
                            v${env.RELEASE_VERSION} ^
                            -m "Release v${env.RELEASE_VERSION} [skip ci]"


                        git push origin ^
                            v${env.RELEASE_VERSION}
                    """
                }
            }
        }


        // =====================================================
        // 18. UPDATE NEXT SNAPSHOT VERSION
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

                    bat """

                        echo ==========================================
                        echo PREPARING NEXT SNAPSHOT
                        echo ==========================================


                        mvn -f backend\\pom.xml versions:set ^
                            -DnewVersion=${env.RELEASE_VERSION}-SNAPSHOT ^
                            -DgenerateBackupPoms=false


                        git config user.name "Jenkins CI"

                        git config user.email "jenkins@company.com"


                        git add backend\\pom.xml


                        git commit ^
                            -m "chore: prepare next version ${env.RELEASE_VERSION}-SNAPSHOT [skip ci]"


                        git push origin main
                    """
                }
            }
        }


        // =====================================================
        // 19. ARCHIVE
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

            Storage Account:
            ${env.AZ_STORAGE_ACCOUNT}

            Backend Container:
            ${env.AZ_BACKEND_CONTAINER}

            Frontend Container:
            ${env.AZ_FRONTEND_CONTAINER}

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

            Base Version:
            ${env.BASE_VERSION}

            Release Version:
            ${env.RELEASE_VERSION}

            ==========================================
            """
        }
    }
}
