pipeline {
    agent any

    triggers {
        githubPush()
    }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Teamtrivenisangam-devops/naukri-automator.git'
            }
        }

        stage('Backend build') {
            steps {
                dir('backend') {
                    bat '''
                        set JAVA_HOME=C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.17.10-hotspot
                        set PATH=%JAVA_HOME%\\bin;%PATH%
                        mvn clean package -DfailIfNoTests=false
                    '''
                }
            }
        }

        stage('Frontend build') {
            steps {
                dir('frontend') {
                    bat 'npm ci'
                    bat 'npm run build'
                }
            }
        }

        stage('Zip frontend dist') {
            steps {
                dir('frontend') {
                    powershell 'Compress-Archive -Path dist\\* -DestinationPath dist.zip -Force'
                }
            }
        }

        stage('Push backend to ACR') {
            steps {
                bat 'az login --identity'
                bat 'oras push naukribackendacr7291.azurecr.io/backend:latest backend/target/naukri-be.jar'
            }
        }

        stage('Push frontend to ACR') {
            steps {
                bat 'oras push naukrifrontendacr7291.azurecr.io/frontend:latest frontend/dist.zip'
            }
        }
    }
}
