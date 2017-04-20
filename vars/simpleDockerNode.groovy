import org.dotnet.ci.util.Agents

// Example:
//
// simpleDockerNode('foo:bar') { <= braces define the closure, implicitly passed as the last parameter
//     checkout scm
//     sh 'echo Hello world'
// }



// Runs a set of functionality on the default node
// that supports docker.
// Parameters:
//  dockerImageName - Docker image to use
//  body - Closure, see example
def call(String dockerImageName, Closure body) {
    call(dockerImageName, 'latest', body)
}

// Runs a set of functionality on the default node
// that supports docker.
// Parameters:
//  dockerImageName - Docker image to use
//  hostVersion - Host VM version.  See Agents.getDockerMachineAffinity for explanation.
//  body - Closure, see example
def call(String dockerImageName, String hostVersion, Closure body) {
    node (Agents.getDockerAgentLabel(hostVersion)) {
        timeout(120) {
            def dockerImage = docker.image(dockerImageName)
            // Force pull.
            retry (3) {
                dockerImage.pull()
            }
            // Important!! The current docker plugin always wants to pass -u hostuid:hostgid
            // to the docker run step.  This is designed to work well with some of the other
            // infrastructure, but causes havoc in our case because there are no non-default (root)
            // users in our container.  This bug is filed as https://issues.jenkins-ci.org/browse/JENKINS-38438.
            // In the meantime, we can pass -u to the inside() step which will append to the command line and
            // override the original -u.
            dockerImage.inside('-u 0:0') {
                try {
                    body()
                }
                finally {
                    // Normally we would use the workspace cleanup plugin to do this cleanup.
                    // However in the latest versions, it utilizes the AsyncResourceDisposer.
                    // This means that we won't attempt to delete the workspace until after the container
                    // has exited.  Outside the container, we won't have permissions to delete the
                    // mapped files (they will be root).
                    echo "Cleaning workspace ${WORKSPACE}"
                    dir(WORKSPACE) {deleteDir()}
                }
            }
        }
    }
}