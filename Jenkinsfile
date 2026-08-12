pipeline {

    agent any

    stages {

        // =====================================================
        // 1. CHECKOUT
        // =====================================================

        stage('1. Checkout') {

            steps {

                echo '===== CHECKOUT SOURCE CODE ====='

                git(
                    branch: 'main',
                    url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git',
                    credentialsId: 'github-credentials'
                )
            }
        }


        // =====================================================
        // 2. VERIFY ENVIRONMENT
        // =====================================================

        stage('2. Verify Environment') {

            steps {

                echo '===== VERIFY ENVIRONMENT ====='

                bat '''

                    echo ===== JAVA =====
                    java -version

                    echo ===== MAVEN =====
                    mvn -version

                    echo ===== NODE =====
                    node -v

                    echo ===== NPM =====
                    npm -v

                    echo ===== GIT =====
                    git --version

                    echo ===== AZURE CLI =====
                    az version

                '''
            }
        }


        // =====================================================
        // 3. FETCH JAVA 17 JRE
        // =====================================================

        stage('3. Fetch Java 17 JRE') {

            steps {

                echo '===== FETCH APPLICATION JRE ====='

                powershell '''

                    & "$env:WORKSPACE\\build\\fetch-jre.ps1"

                    if ($LASTEXITCODE -ne 0) {

                        exit $LASTEXITCODE
                    }

                '''
            }
        }


        // =====================================================
        // 4. INSTALL PLAYWRIGHT CHROMIUM
        // =====================================================

        stage('4. Install Playwright Chromium') {

            steps {

                echo '===== INSTALL PLAYWRIGHT CHROMIUM ====='

                powershell '''

                    & "$env:WORKSPACE\\build\\install-playwright.ps1"

                    if ($LASTEXITCODE -ne 0) {

                        exit $LASTEXITCODE
                    }

                '''
            }
        }


        // =====================================================
        // 5. BUILD BACKEND
        // =====================================================

        stage('5. Build Backend') {

            steps {

                echo '===== BUILD BACKEND ====='

                bat '''

                    mvn -f backend\\pom.xml clean package ^
                        -DskipTests ^
                        -Dmaven.test.skip=true

                '''
            }
        }


        // =====================================================
        // 6. BUILD MOCK SERVER
        // =====================================================

        stage('6. Build Mock Server') {

            steps {

                echo '===== BUILD MOCK SERVER ====='

                bat '''

                    mvn -f mock-naukri\\pom.xml clean package ^
                        -DskipTests ^
                        -Dmaven.test.skip=true

                '''
            }
        }


        // =====================================================
        // 7. BUILD FRONTEND
        // =====================================================

        stage('7. Build Frontend') {

            steps {

                echo '===== BUILD FRONTEND ====='

                powershell '''

                    & "$env:WORKSPACE\\build\\phases\\build-frontend.ps1"

                    if ($LASTEXITCODE -ne 0) {

                        exit $LASTEXITCODE
                    }

                '''
            }
        }


        // =====================================================
        // 7b. SONARQUBE
        // =====================================================

        stage('7b. SonarQube Analysis') {

            steps {

                echo '===== SONARQUBE ANALYSIS ====='

                script {

                    def scannerHome =
                        tool 'SonarScanner'


                    withSonarQubeEnv('SonarQubeServer') {

                        bat "\"${scannerHome}\\bin\\sonar-scanner.bat\""

                    }
                }
            }
        }


        // =====================================================
        // 8. BUILD ELECTRON
        // =====================================================

        stage('8. Build Electron Application') {

            steps {

                echo '===== BUILD ELECTRON APPLICATION ====='

                powershell '''

                    & "$env:WORKSPACE\\build\\phases\\build-electron.ps1" `
                        -Variant Ship

                    if ($LASTEXITCODE -ne 0) {

                        exit $LASTEXITCODE
                    }

                '''
            }
        }


        // =====================================================
        // 9. VERIFY ARTIFACTS
        // =====================================================

        stage('9. Verify Artifacts') {

            steps {

                echo '===== VERIFY ARTIFACTS ====='

                powershell '''

                    $dist =
                        "$env:WORKSPACE\\dist"


                    if (-not (Test-Path $dist)) {

                        throw "dist directory does not exist"
                    }


                    Write-Host ""

                    Write-Host "===== BUILD ARTIFACTS ====="


                    Get-ChildItem $dist -Recurse -File |
                        Select-Object FullName, Length


                    $exeFiles =
                        Get-ChildItem `
                        $dist `
                        -Recurse `
                        -Filter "*.exe"


                    if ($exeFiles.Count -eq 0) {

                        throw "No EXE artifacts found"
                    }


                    Write-Host ""

                    Write-Host "===== EXE ARTIFACTS FOUND ====="


                    foreach ($exe in $exeFiles) {

                        Write-Host $exe.FullName
                    }


                    Write-Host ""

                    Write-Host "Artifact verification SUCCESS"

                '''
            }
        }


        // =====================================================
        // 10. ARCHIVE ARTIFACTS
        // =====================================================

        stage('10. Archive Artifacts') {

            steps {

                echo '===== ARCHIVING ARTIFACTS ====='

                archiveArtifacts(
                    artifacts: 'dist/**/*.exe',
                    fingerprint: true
                )
            }
        }


        // =====================================================
        // 11. UPLOAD TO AZURE BLOB STORAGE
        // =====================================================

        stage('11. Upload to Azure Blob Storage') {

            steps {

                echo '===== UPLOADING TO AZURE BLOB STORAGE ====='

                azureUpload(

                    containerName: 'naukrifrontend7291',

                    storageType: 'blobstorage',

                    filesPath: 'dist/**/*.exe',

                    storageCredentialId: 'azure-storage-cred'

                )
            }
        }
    }


    // =========================================================
    // POST ACTIONS
    // =========================================================

    post {

        success {

            echo '''

            ========================================
                 NAUKRI CI BUILD SUCCESS
            ========================================

            Artifacts successfully generated,
            archived and uploaded to Azure Blob Storage.

            ========================================

            '''
        }


        failure {

            echo '''

            ========================================
                 NAUKRI CI BUILD FAILED
            ========================================

            Check the first failed stage
            in Console Output.

            ========================================

            '''
        }


        always {

            echo '===== Jenkins CI pipeline finished ====='

        }
    }
}
