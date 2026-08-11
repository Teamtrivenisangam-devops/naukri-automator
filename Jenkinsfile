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
 tools {
        jdk 'Java17'
        maven 'Maven3'
    }

    stages 
        stage('Verify Java') {
            steps {
                bat '''
                    java -version
                    mvn -version
                '''
            }
        }
    environment {

        // =====================================================
        // JAVA
        // =====================================================

        JAVA_HOME = 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot'

        PATH = "${JAVA_HOME}\\bin;${env.PATH}"


        // =====================================================
        // AZURE STORAGE
        // =====================================================

        AZ_STORAGE_ACCOUNT = 'naukristorage7291'

        AZ_BACKEND_CONTAINER = 'naukribackend7291'

        AZ_FRONTEND_CONTAINER = 'naukrifrontend7291'
    }


    


        // =====================================================
        // 1. CHECKOUT
        // =====================================================

        stage('Checkout') {

            steps {

                git(
                    branch: 'main',

                    url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git',

                    credentialsId: 'github-credentials'
                )
            }
        }


        // =====================================================
        // 2. SKIP CI CHECK
        // =====================================================

        stage('Skip CI Check') {

            steps {

                script {

                    def lastMsg = bat(
                        script: '@git log -1 --pretty=%%B',
                        returnStdout: true
                    ).trim()


                    echo "Latest commit message:"
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


                    echo "=========================================="
                    echo "MAVEN VERSION"
                    echo "=========================================="
                    echo "POM Version: ${pomVersion}"


                    // Remove SNAPSHOT

                    def baseVersion =
                        pomVersion
                            .replace('-SNAPSHOT', '')
                            .trim()


                    // Validate version

                    def parts =
                        baseVersion.tokenize('.')


                    if (parts.size() != 3) {

                        error(
                            "Invalid Maven version: ${pomVersion}. " +
                            "Expected major.minor.patch"
                        )
                    }


                    def major =
                        parts[0].toInteger()

                    def minor =
                        parts[1].toInteger()

                    def patch =
                        parts[2].toInteger()


                    // Increment patch

                    def releaseVersion =
                        "${major}.${minor}.${patch + 1}"


                    echo "Base Version    : ${baseVersion}"

                    echo "Release Version : ${releaseVersion}"


                    // =================================================
                    // SAVE VERSION TO FILE
                    // =================================================

                    writeFile(
                        file: 'base-version.txt',
                        text: baseVersion
                    )


                    writeFile(
                        file: 'release-version.txt',
                        text: releaseVersion
                    )


                    // Also expose as environment variables
                    // for normal full pipeline execution.

                    env.BASE_VERSION =
                        baseVersion

                    env.RELEASE_VERSION =
                        releaseVersion


                    echo "=========================================="
                    echo "VERSION CALCULATION SUCCESSFUL"
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

                    if (!fileExists('release-version.txt')) {

                        error(
                            'release-version.txt not found. ' +
                            'Run the pipeline from Checkout using Build Now.'
                        )
                    }


                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    def baseVersion =
                        readFile(
                            'base-version.txt'
                        ).trim()


                    if (!releaseVersion) {

                        error(
                            'Release version is empty.'
                        )
                    }


                    if (!baseVersion) {

                        error(
                            'Base version is empty.'
                        )
                    }


                    env.BASE_VERSION =
                        baseVersion

                    env.RELEASE_VERSION =
                        releaseVersion


                    echo "=========================================="
                    echo "VERSION VERIFIED"
                    echo "=========================================="
                    echo "Base Version    : ${baseVersion}"
                    echo "Release Version : ${releaseVersion}"
                    echo "=========================================="
                }
            }
        }


        // =====================================================
        // 6. SET RELEASE VERSION
        // =====================================================

        stage('Set Release Version') {

            steps {

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    bat """
                        echo ==========================================
                        echo SET MAVEN RELEASE VERSION
                        echo ==========================================

                        echo Release Version: ${releaseVersion}

                        mvn -f backend\\pom.xml versions:set ^
                            -DnewVersion=${releaseVersion} ^
                            -DgenerateBackupPoms=false
                    """
                }
            }
        }


        // =====================================================
        // 7. BUILD BACKEND
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
                        echo BUILD FRONTEND
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

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    powershell """

                        \$releaseDir =
                            "\$env:WORKSPACE\\release-artifacts"


                        \$backendDir =
                            "\$releaseDir\\backend"


                        \$frontendDir =
                            "\$releaseDir\\frontend"


                        # ==========================================
                        # CLEAN
                        # ==========================================

                        if (Test-Path \$releaseDir) {

                            Remove-Item `
                                \$releaseDir `
                                -Recurse `
                                -Force
                        }


                        # ==========================================
                        # CREATE DIRECTORIES
                        # ==========================================

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


                        if (-not (Test-Path \$jar)) {

                            throw "Backend JAR not found"
                        }


                        Copy-Item `
                            \$jar `
                            "\$backendDir\\naukri-be-${releaseVersion}.jar" `
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
                                "-${releaseVersion}.exe"


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


                        if %ERRORLEVEL% NEQ 0 (

                            echo Azure login failed

                            exit /b %ERRORLEVEL%
                        )


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

        stage('Verify Azure Storage Access') {

            steps {

                bat '''

                    echo ==========================================
                    echo VERIFYING BACKEND STORAGE
                    echo ==========================================


                    az storage container show ^
                        --name "%AZ_BACKEND_CONTAINER%" ^
                        --account-name "%AZ_STORAGE_ACCOUNT%" ^
                        --auth-mode login ^
                        -o table


                    if %ERRORLEVEL% NEQ 0 (

                        echo Backend container verification failed

                        exit /b %ERRORLEVEL%
                    )


                    echo ==========================================
                    echo VERIFYING FRONTEND STORAGE
                    echo ==========================================


                    az storage container show ^
                        --name "%AZ_FRONTEND_CONTAINER%" ^
                        --account-name "%AZ_STORAGE_ACCOUNT%" ^
                        --auth-mode login ^
                        -o table


                    if %ERRORLEVEL% NEQ 0 (

                        echo Frontend container verification failed

                        exit /b %ERRORLEVEL%
                    )


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

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    bat """

                        echo ==========================================
                        echo UPLOADING BACKEND
                        echo ==========================================


                        echo Storage Account:
                        echo ${env.AZ_STORAGE_ACCOUNT}


                        echo Container:
                        echo ${env.AZ_BACKEND_CONTAINER}


                        echo Version:
                        echo ${releaseVersion}


                        az storage blob upload-batch ^
                            --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                            --destination "${env.AZ_BACKEND_CONTAINER}" ^
                            --source "release-artifacts\\backend" ^
                            --destination-path "${releaseVersion}" ^
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
        }


        // =====================================================
        // 15. UPLOAD FRONTEND
        // =====================================================

        stage('Upload Frontend') {

            steps {

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    bat """

                        echo ==========================================
                        echo UPLOADING FRONTEND
                        echo ==========================================


                        echo Storage Account:
                        echo ${env.AZ_STORAGE_ACCOUNT}


                        echo Container:
                        echo ${env.AZ_FRONTEND_CONTAINER}


                        echo Version:
                        echo ${releaseVersion}


                        az storage blob upload-batch ^
                            --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                            --destination "${env.AZ_FRONTEND_CONTAINER}" ^
                            --source "release-artifacts\\frontend" ^
                            --destination-path "${releaseVersion}" ^
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
        }


        // =====================================================
        // 16. VERIFY UPLOAD
        // =====================================================

        stage('Verify Upload') {

            steps {

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    bat """

                        echo ==========================================
                        echo VERIFYING BACKEND UPLOAD
                        echo ==========================================


                        az storage blob list ^
                            --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                            --container-name "${env.AZ_BACKEND_CONTAINER}" ^
                            --prefix "${releaseVersion}/" ^
                            --auth-mode login ^
                            -o table


                        echo ==========================================
                        echo VERIFYING FRONTEND UPLOAD
                        echo ==========================================


                        az storage blob list ^
                            --account-name "${env.AZ_STORAGE_ACCOUNT}" ^
                            --container-name "${env.AZ_FRONTEND_CONTAINER}" ^
                            --prefix "${releaseVersion}/" ^
                            --auth-mode login ^
                            -o table
                    """
                }
            }
        }


        // =====================================================
        // 17. GIT TAG
        // =====================================================

        stage('Tag Release') {

            steps {

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


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
                            echo CREATING GIT TAG
                            echo ==========================================


                            git config user.name "Jenkins CI"

                            git config user.email "jenkins@company.com"


                            git tag -a ^
                                v${releaseVersion} ^
                                -m "Release v${releaseVersion} [skip ci]"


                            git push origin ^
                                v${releaseVersion}
                        """
                    }
                }
            }
        }


        // =====================================================
        // 18. PREPARE NEXT SNAPSHOT
        // =====================================================

        stage('Prepare Next Snapshot') {

            steps {

                script {

                    def releaseVersion =
                        readFile(
                            'release-version.txt'
                        ).trim()


                    def nextVersion =
                        "${releaseVersion}-SNAPSHOT"


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
                            echo PREPARING NEXT DEVELOPMENT VERSION
                            echo ==========================================


                            echo Next version:
                            echo ${nextVersion}


                            mvn -f backend\\pom.xml versions:set ^
                                -DnewVersion=${nextVersion} ^
                                -DgenerateBackupPoms=false


                            git config user.name "Jenkins CI"

                            git config user.email "jenkins@company.com"


                            git add backend\\pom.xml


                            git commit ^
                                -m "chore: prepare next version ${nextVersion} [skip ci]"


                            git push origin main
                        """
                    }
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

            cleanWs(
                deleteDirs: true,
                disableDeferredWipeout: true,
                notFailBuild: true
            )
        }


        success {

            script {

                def releaseVersion =
                    fileExists('release-version.txt')
                    ? readFile('release-version.txt').trim()
                    : 'unknown'


                echo """
                ==========================================
                    RELEASE SUCCESSFUL
                ==========================================

                Jenkins Build:
                ${env.BUILD_NUMBER}

                Release Version:
                ${releaseVersion}

                Storage Account:
                ${env.AZ_STORAGE_ACCOUNT}

                Backend Container:
                ${env.AZ_BACKEND_CONTAINER}

                Frontend Container:
                ${env.AZ_FRONTEND_CONTAINER}

                Git Tag:
                v${releaseVersion}

                ==========================================
                """
            }
        }


        failure {

            echo """
            ==========================================
                    RELEASE FAILED
            ==========================================

            Check the failed stage above.

            ==========================================
            """
        }
    }
}
}
