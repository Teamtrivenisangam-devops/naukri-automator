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
        // 7b. SONARQUBE ANALYSIS
        // =====================================================

        stage('7b. SonarQube Analysis') {

            steps {

                echo '===== SONARQUBE ANALYSIS ====='

                script {

                    def scannerHome = tool 'SonarScanner'

                    withSonarQubeEnv('SonarQube') {

                        bat "\"${scannerHome}\\bin\\sonar-scanner.bat\""

                    }
                }
            }
        }


        // =====================================================
        // 8. BUILD ELECTRON APPLICATION
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
        // 9. VERIFY ALL ARTIFACTS
        // =====================================================

        stage('9. Verify Artifacts') {

            steps {

                echo '===== VERIFY ALL ARTIFACTS ====='

                powershell '''

                    $backendDir =
                        "$env:WORKSPACE\\backend\\target"

                    $mockDir =
                        "$env:WORKSPACE\\mock-naukri\\target"

                    $frontendDir =
                        "$env:WORKSPACE\\dist"


                    # =================================================
                    # VERIFY DIRECTORIES
                    # =================================================

                    if (-not (Test-Path $backendDir)) {

                        throw "Backend target directory does not exist"

                    }


                    if (-not (Test-Path $mockDir)) {

                        throw "Mock server target directory does not exist"

                    }


                    if (-not (Test-Path $frontendDir)) {

                        throw "Frontend dist directory does not exist"

                    }


                    # =================================================
                    # FIND BACKEND JAR
                    # =================================================

                    $backendJars =
                        Get-ChildItem `
                        $backendDir `
                        -Recurse `
                        -Filter "*.jar" |
                        Where-Object {
                            $_.Name -notlike "*original*.jar"
                        }


                    # =================================================
                    # FIND MOCK SERVER JAR
                    # =================================================

                    $mockJars =
                        Get-ChildItem `
                        $mockDir `
                        -Recurse `
                        -Filter "*.jar" |
                        Where-Object {
                            $_.Name -notlike "*original*.jar"
                        }


                    # =================================================
                    # FIND FRONTEND EXE
                    # =================================================

                    $exeFiles =
                        Get-ChildItem `
                        $frontendDir `
                        -Recurse `
                        -Filter "*.exe"


                    # =================================================
                    # VALIDATE BACKEND
                    # =================================================

                    if ($backendJars.Count -eq 0) {

                        throw "No Backend JAR artifacts found"

                    }


                    # =================================================
                    # VALIDATE MOCK SERVER
                    # =================================================

                    if ($mockJars.Count -eq 0) {

                        throw "No Mock Server JAR artifacts found"

                    }


                    # =================================================
                    # VALIDATE FRONTEND
                    # =================================================

                    if ($exeFiles.Count -eq 0) {

                        throw "No Frontend EXE artifacts found"

                    }


                    # =================================================
                    # DISPLAY BACKEND
                    # =================================================

                    Write-Host ""
                    Write-Host "========================================"
                    Write-Host "BACKEND ARTIFACTS"
                    Write-Host "========================================"

                    foreach ($jar in $backendJars) {

                        Write-Host $jar.FullName

                    }


                    # =================================================
                    # DISPLAY MOCK SERVER
                    # =================================================

                    Write-Host ""
                    Write-Host "========================================"
                    Write-Host "MOCK SERVER ARTIFACTS"
                    Write-Host "========================================"

                    foreach ($jar in $mockJars) {

                        Write-Host $jar.FullName

                    }


                    # =================================================
                    # DISPLAY FRONTEND
                    # =================================================

                    Write-Host ""
                    Write-Host "========================================"
                    Write-Host "FRONTEND ARTIFACTS"
                    Write-Host "========================================"

                    foreach ($exe in $exeFiles) {

                        Write-Host $exe.FullName

                    }


                    Write-Host ""
                    Write-Host "========================================"
                    Write-Host "ARTIFACT VERIFICATION SUCCESS"
                    Write-Host "========================================"

                '''
            }
        }


        // =====================================================
        // 10. ARCHIVE BACKEND ARTIFACT
        // =====================================================

        stage('10. Archive Backend Artifact') {

            steps {

                echo '===== ARCHIVING BACKEND ARTIFACT ====='

                archiveArtifacts(
                    artifacts: 'backend/target/*.jar',
                    fingerprint: true
                )
            }
        }


        // =====================================================
        // 11. ARCHIVE MOCK SERVER ARTIFACT
        // =====================================================

        stage('11. Archive Mock Server Artifact') {

            steps {

                echo '===== ARCHIVING MOCK SERVER ARTIFACT ====='

                archiveArtifacts(
                    artifacts: 'mock-naukri/target/*.jar',
                    fingerprint: true
                )
            }
        }


        // =====================================================
        // 12. ARCHIVE FRONTEND ARTIFACT
        // =====================================================

        stage('12. Archive Frontend Artifact') {

            steps {

                echo '===== ARCHIVING FRONTEND ARTIFACT ====='

                archiveArtifacts(
                    artifacts: 'dist/**/*.exe',
                    fingerprint: true
                )
            }
        }


        // =====================================================
        // 13. UPLOAD BACKEND TO AZURE BLOB
        // =====================================================

        stage('13. Upload Backend to Azure Blob') {

            steps {

                echo '===== UPLOADING BACKEND TO AZURE BLOB ====='

                azureUpload(

                    containerName: 'naukribackend7291',

                    storageType: 'blobstorage',

                    filesPath: 'backend/target/*.jar',

                    storageCredentialId: 'azure-storage-cred'

                )
            }
        }


        // =====================================================
        // 14. UPLOAD MOCK SERVER TO AZURE BLOB
        // =====================================================

        stage('14. Upload Mock Server to Azure Blob') {

            steps {

                echo '===== UPLOADING MOCK SERVER TO AZURE BLOB ====='

                azureUpload(

                    containerName: 'naukrimock7291',

                    storageType: 'blobstorage',

                    filesPath: 'mock-naukri/target/*.jar',

                    storageCredentialId: 'azure-storage-cred'

                )
            }
        }


        // =====================================================
        // 15. UPLOAD FRONTEND TO AZURE BLOB
        // =====================================================

        stage('15. Upload Frontend to Azure Blob') {

            steps {

                echo '===== UPLOADING FRONTEND TO AZURE BLOB ====='

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

            Backend JAR successfully generated
            Mock Server JAR successfully generated
            Frontend EXE successfully generated

            All artifacts archived in Jenkins
            and uploaded to Azure Blob Storage.

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
