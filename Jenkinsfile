pipeline {
    agent any

    parameters {
        string(
            name: 'GIT_BRANCH',
            defaultValue: 'main',
            description: '要构建的远程分支，例如 main、develop 或 feature/demo'
        )
        string(
            name: 'GITHUB_SHA',
            defaultValue: '',
            description: '可选：指定提交 SHA；填写后优先于 GIT_BRANCH'
        )
        booleanParam(
            name: 'RUN_EXTERNAL_TESTS',
            defaultValue: false,
            description: 'Run tests that require external services such as a real MQTT broker'
        )
        booleanParam(
            name: 'DEPLOY_AFTER_BUILD',
            defaultValue: false,
            description: '成功构建后通过受限宿主机 webhook 重启后端应用容器'
        )
    }

    options {
        skipDefaultCheckout(true)
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    def commitSha = params.GITHUB_SHA.trim()
                    def branchName = params.GIT_BRANCH.trim().replaceFirst('^origin/', '')

                    if (commitSha && !(commitSha ==~ /[0-9a-fA-F]{7,40}/)) {
                        error('GITHUB_SHA 必须是 7 到 40 位十六进制提交 SHA')
                    }
                    if (!commitSha) {
                        withEnv(["REQUESTED_BRANCH=${branchName}"]) {
                            sh 'git check-ref-format --branch "$REQUESTED_BRANCH"'
                        }
                    }

                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: commitSha ?: "*/${branchName}"]],
                        userRemoteConfigs: [[
                            url: env.CLOUD_REPOSITORY_URL,
                            credentialsId: 'lab-system-cloud-deploy-key',
                            refspec: '+refs/heads/*:refs/remotes/origin/*'
                        ]]
                    ])
                }
                sh 'echo "Building commit $(git rev-parse HEAD)"'
            }
        }

        stage('Verify') {
            steps {
                script {
                    def externalTestsProfile = params.RUN_EXTERNAL_TESTS ? '-Pexternal-tests' : ''
                    sh """
                      ./mvnw --batch-mode --no-transfer-progress \
                        clean verify ${externalTestsProfile}
                    """
                }
            }
        }

        stage('Deploy') {
            when {
                expression { params.DEPLOY_AFTER_BUILD }
            }
            steps {
                sh '''
                  test -n "$LAB_DEPLOY_WEBHOOK_URL"
                  test -r "$LAB_DEPLOY_WEBHOOK_TOKEN_FILE"
                  curl --fail-with-body --show-error --silent \
                    --header "Authorization: Bearer $(cat "$LAB_DEPLOY_WEBHOOK_TOKEN_FILE")" \
                    --header "Content-Type: application/json" \
                    --data '{}' \
                    "${LAB_DEPLOY_WEBHOOK_URL%/}/v1/deploy/cloud"
                '''
            }
        }
    }

    post {
        always {
            junit(
                allowEmptyResults: true,
                testResults: '**/target/surefire-reports/*.xml,**/target/failsafe-reports/*.xml'
            )
            archiveArtifacts allowEmptyArchive: true, artifacts: '**/target/*.jar', fingerprint: true
        }
    }
}
