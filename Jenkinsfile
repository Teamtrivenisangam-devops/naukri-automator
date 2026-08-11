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
        // BACKEND CONTAINER
        // =====================================================

        AZ_BACKEND_CONTAINER =
            'naukribackend7291'


        // =====================================================
        // FRONTEND CONTAINER
        // =====================================================

        AZ_FRONTEND_CONTAINER =
            'naukrifrontend7291'


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
                        ).trim()


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
        // 4. DETERMINE MAVEN BASE VERSION
        // =====================================================
        //
        // IMPORTANT:
        // `mvn help:evaluate -q -DforceStdout` can still emit stray
        // lines (plugin download notices, warnings) on some Maven /
        // network conditions, even with -q. Capturing that output
        // and calling .trim() on the WHOLE block is what silently
        // produced a "version" string like:
        //
        //   Downloading ...
        //   0.1.0
        //
        // ...which then failed strict parsing and left
        // BASE_VERSION / RELEASE_VERSION as null further down,
        // without the pipeline necessarily hard-failing at that
        // exact point.
        //
        // Fix: only trust the LAST non-blank line of stdout, and
        // strictly validate it against a semver-ish regex before
        // using it. If it doesn't match, fail immediately with a
        // clear message instead of silently producing null.
        //
        // =====================================================

        stage('Determine Base Version') {

            steps {

                script {

                    def rawOutput =
                        bat(
                            script:
                                '''
                                @mvn -f backend\\pom.xml help:evaluate ^
                                -Dexpression=project.version ^
                                -q -B -DforceStdout
                                ''',

                            returnStdout: true
                        )


                    echo "---- RAW Maven output ----"
                    echo rawOutput
                    echo "---------------------------"


                    // Take only the last non-blank line — this is
                    // where -DforceStdout actually places the value,
                    // regardless of any noise printed above it.

                    def lines =
                        rawOutput
                            .readLines()
                            .collect { it.trim() }
                            .findAll { it }


                    if (lines.isEmpty()) {

                        error(
                            "Could not read Maven project version: " +
                            "mvn produced no usable output."
                        )
                    }


                    def pomVersion =
                        lines[-1]


                    echo "Maven POM version: ${pomVersion}"


                    def baseVersion =
                        pomVersion.replace(
                            '-SNAPSHOT',
                            ''
                        )


                    // Strict validation BEFORE we trust this value.
                    // Anything that doesn't look like major.minor.patch
                    // fails the build here, with a clear reason,
                    // instead of quietly becoming null three stages
                    // later.

                    if (!(baseVersion ==~ /^\d+\.\d+\.\d+$/)) {

                        error(
                            "Invalid Maven version parsed: '${pomVersion}' " +
                            "(cleaned: '${baseVersion}'). " +
                            "Expected format major.minor.patch, e.g. 0.1.0. " +
                            "Check backend\\pom.xml <version> and make sure " +
                            "'mvn help:evaluate -q -DforceStdout' isn't " +
                            "printing extra lines in this environment."
                        )
                    }


                    def parts =
                        baseVersion.tokenize('.')

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
        // 5. VALIDATE RELEASE VERSION
        // =====================================================
        //
        // Hard safety gate. If RELEASE_VERSION somehow still isn't
        // set at this point (partial restart, future refactor,
        // whatever), stop here with a clear message instead of
        // letting `az storage blob upload-batch` fail later with a
        // cryptic "--destination-path: expected one argument".
        //
        // =====================================================

        stage('Validate Release Version') {

            steps {

                script {

                    if (!env.RELEASE_VERSION?.trim()) {

                        error(
                            "RELEASE_VERSION is not set. " +
                            "This pipeline must be run as a full build " +
                            "from 'Checkout' — do not use 'Restart from " +
                            "Stage' past the 'Determine Base Version' " +
                            "stage, since RELEASE_VERSION is computed " +
                            "there and required by every stage after it."
                        )
                    }


                    if (!(env.RELEASE_VERSION ==~ /^\d+\.\d+\.\d+$/)) {

                        error(
                            "RELEASE_VERSION has an unexpected value: " +
                            "'${env.RELEASE_VERSION}'"
                        )
                    }
                }
            }
        }


        // =====================================================
        // 6. SET RELEASE VERSION
        // =====================================================

        stage('Set Release Version') {

            steps {

                bat '''
                    echo Setting Maven release version...

                    mvn -f backend\\pom.xml versions:set ^
                        -DnewVersion=%RELEASE_VERSION% ^
                        -DgenerateBackupPoms=false

                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

                    echo.
                    echo Verifying version was applied...

                    mvn -f backend\\pom.xml help:evaluate ^
                        -Dexpression=project.version ^
                        -q -B -DforceStdout
                '''
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

                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
                '''
            }
        }


        // =====================================================
        // 8. VERIFY BACKEND
        // =====================================================

        stage('Verify Backend Artifact') {

            steps {

                powershell '''

                    $jarPath =
                        "$env:WORKSPACE\\backend\\target\\naukri-be.jar"


                    if (-not (Test-Path $jarPath)) {

                        throw "Backend JAR not found: $jarPath"
                    }


                    Write-Host "Backend JAR found:"
                    Write-Host $jarPath
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

                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%

                        npm run build

                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
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


                    Write-Host "Electron EXE found."
                '''
            }
        }


        // =====================================================
        // 11. PREPARE ARTIFACTS
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


                    Copy-Item `
                        $jarPath `
                        "$backendDir\\naukri-be-$env:RELEASE_VERSION.jar" `
                        -Force


                    # ==========================================
                    # FRONTEND
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
                    # ELECTRON
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


                    Write-Host ""
                    Write-Host "=========================================="
                    Write-Host "ARTIFACTS READY"
                    Write-Host "=========================================="


                    Get-ChildItem `
                        $releaseDir `
                        -Recurse
                '''
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


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        echo Azure login successful.
                    '''
                }
            }
        }


        // =====================================================
        // 13. VERIFY CONTAINERS
        // =====================================================

        stage('Verify Azure Storage Access') {

            steps {

                bat '''

                    echo ==========================================
                    echo VERIFYING BACKEND CONTAINER
                    echo ==========================================


                    az storage container show ^
                        --name %AZ_BACKEND_CONTAINER% ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --auth-mode login ^
                        -o table


                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                    echo ==========================================
                    echo VERIFYING FRONTEND CONTAINER
                    echo ==========================================


                    az storage container show ^
                        --name %AZ_FRONTEND_CONTAINER% ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --auth-mode login ^
                        -o table


                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                    echo ==========================================
                    echo BOTH CONTAINERS ARE ACCESSIBLE
                    echo ==========================================
                '''
            }
        }


        // =====================================================
        // 14. UPLOAD BACKEND
        // =====================================================

        stage('Upload Backend') {

            steps {

                bat '''

                    echo ==========================================
                    echo UPLOADING BACKEND
                    echo ==========================================
                    echo Release version: %RELEASE_VERSION%


                    az storage blob upload-batch ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --destination %AZ_BACKEND_CONTAINER% ^
                        --source release-artifacts\\backend ^
                        --destination-path %RELEASE_VERSION% ^
                        --auth-mode login


                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                    echo Backend upload successful.
                '''
            }
        }


        // =====================================================
        // 15. UPLOAD FRONTEND
        // =====================================================

        stage('Upload Frontend') {

            steps {

                bat '''

                    echo ==========================================
                    echo UPLOADING FRONTEND
                    echo ==========================================
                    echo Release version: %RELEASE_VERSION%


                    az storage blob upload-batch ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --destination %AZ_FRONTEND_CONTAINER% ^
                        --source release-artifacts\\frontend ^
                        --destination-path %RELEASE_VERSION% ^
                        --auth-mode login


                    if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                    echo Frontend upload successful.
                '''
            }
        }


        // =====================================================
        // 16. VERIFY UPLOAD
        // =====================================================

        stage('Verify Upload') {

            steps {

                bat '''

                    echo ==========================================
                    echo BACKEND ARTIFACTS
                    echo ==========================================


                    az storage blob list ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --container-name %AZ_BACKEND_CONTAINER% ^
                        --prefix %RELEASE_VERSION% ^
                        --auth-mode login ^
                        -o table


                    echo ==========================================
                    echo FRONTEND ARTIFACTS
                    echo ==========================================


                    az storage blob list ^
                        --account-name %AZ_STORAGE_ACCOUNT% ^
                        --container-name %AZ_FRONTEND_CONTAINER% ^
                        --prefix %RELEASE_VERSION% ^
                        --auth-mode login ^
                        -o table
                '''
            }
        }


        // =====================================================
        // 17. TAG RELEASE
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
                        echo TAGGING RELEASE
                        echo ==========================================


                        git config user.name "Jenkins CI"

                        git config user.email "jenkins@company.com"


                        git tag -a ^
                            v%RELEASE_VERSION% ^
                            -m "Release v%RELEASE_VERSION% [skip ci]"


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        git push origin ^
                            v%RELEASE_VERSION%


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
                    '''
                }
            }
        }


        // =====================================================
        // 18. PREPARE NEXT SNAPSHOT
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
                        echo PREPARING NEXT VERSION
                        echo ==========================================


                        mvn -f backend\\pom.xml versions:set ^
                            -DnewVersion=%RELEASE_VERSION%-SNAPSHOT ^
                            -DgenerateBackupPoms=false


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        git config user.name "Jenkins CI"

                        git config user.email "jenkins@company.com"


                        git add backend\\pom.xml


                        git commit ^
                            -m "chore: prepare next development version %RELEASE_VERSION%-SNAPSHOT [skip ci]"


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%


                        git push origin main


                        if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
                    '''
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
                notFailBuild: true
            )
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

            Candidate Version:
            ${env.RELEASE_VERSION}

            Maven version will not be advanced
            if the release process fails.

            ==========================================
            """
        }
    }
}
