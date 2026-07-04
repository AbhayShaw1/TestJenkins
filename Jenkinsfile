pipeline {
    agent any

    stages {

        stage('compile') {
            steps {
                echo 'Compiling code'
                bat 'mvn compile > compile_report.txt 2>&1'
            }

        }

        stage('build') {
            steps {

                echo 'Building the maven package without running tests'
                bat 'mvn clean package -Dmaven.test.skip=true > build_report.txt 2>&1'
            }
        }

        stage('test') {
            steps  {
                echo 'Running the JUnit tests'
                bat 'mvn test > test_report.txt 2>&1'
            }
        }

        stage('deploy') {
            steps {
                echo 'Deploying the Application'
                bat '''
                    mkdir %USERPROFILE%\\deploy
                    copy target\\ehps_api.war %USERPROFILE%\\deploy\\ehps_api.war /Y
                '''

                dir('%USERPROFILE%\\deploy') {

                    // Stop Old process if running
                    bat 'taskkill /fi /fi "IMAGENAME eq java" /fi "WINDOWTITLE eq ehps_api*"'

                    sleep 5

                    withEnv(['JENKINS_NODE_COOKIE=dontKillMe']) {
                        bat 'start "ehps_api" /b javaw -jar ehps_api.war > app.log 2>&1'
                    }
                }
            }
        }
    }

    post {
        success {
            echo "Builds & Tests sucessful"
        }

        always {
            echo 'Saving artifacts'
            archiveArtifacts artifacts: 'target\\*.war, target\\*.jar, **\\build_report.txt, **\\test_report.txt, **\\compile_report.txt', followSymlinks: false, allowEmptyArchive: true
            echo 'Cleaning Workspace'
            cleanWs()
        }
    }
}