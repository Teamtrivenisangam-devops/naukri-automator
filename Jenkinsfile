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
        FRONTEND_ACR = 'naukrifrontendacr7291.azurecr.io'
        IMAGE_TAG    = "${env.BUILD_NUMBER}"
    }

    stages {

        stage('Preflight checks') {
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

        stage('Build') {
            parallel {

                stage('Backend') {
                    steps {
                        dir('backend') {
                            retry(2) {
                                bat 'mvn clean package -DfailIfNoTests=false'
                            }
                        }
                    }

                    post {
                        success {
                            script {
                                def jar = 'backend/target/naukri-be.jar'

                                if (!fileExists(jar)) {
                                    error "Backend build reported success but ${jar} is missing"
                                }
                            }
                        }
                    }
                }

                stage('Frontend') {
                    steps {
                        dir('frontend') {

                            retry(2) {
                                bat 'npm ci'
                            }

                            bat 'npm run build'

                            powershell '''
                                if (-not (Test-Path "dist")) {
                                    throw "frontend/dist missing after build"
                                }

                                Compress-Archive `
                                    -Path dist\\* `
                                    -DestinationPath dist.zip `
                                    -Force
                            '''
                        }
                    }

                    post {
                        success {
                            script {
                                if (!fileExists('frontend/dist.zip')) {
                                    error "Frontend build reported success but dist.zip is missing"
                                }
                            }
                        }
                    }
                }
            }
        }

        stage('Azure Login') {
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

        stage('Push to ACR') {
            parallel {

                stage('Push Backend') {
                    steps {
                        bat "oras push ${BACKEND_ACR}/backend:${IMAGE_TAG} backend/target/naukri-be.jar"
                        bat "oras push ${BACKEND_ACR}/backend:latest backend/target/naukri-be.jar"
                    }
                }

                stage('Push Frontend') {
                    steps {
                        bat "oras push ${FRONTEND_ACR}/frontend:${IMAGE_TAG} frontend/dist.zip"
                        bat "oras push ${FRONTEND_ACR}/frontend:latest frontend/dist.zip"
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Build ${env.BUILD_NUMBER} pushed backend/frontend as tag ${IMAGE_TAG} and latest."
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
