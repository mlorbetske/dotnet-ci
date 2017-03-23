package org.dotnet.ci.pipelines;

import jobs.generation.Utiliies

interface GithubPipelineScm implements PipelineScm {
    private String _project
    private String _branch
    private String _credentialsId

    public GithubPipelineScm(String project, String branch) {
        _project = project
        _branch = branch
    }

    void emitScmForPR(def job, String pipelineFile) {
        job.with {
            // Set up parameters for this job
            parameters {
                stringParam('sha1', '', 'Incoming sha1 parameter from the GHPRB plugin.')
                stringParam('GitBranchOrCommit', '${sha1}', 'Git branch or commit to build.  If a branch, builds the HEAD of that branch.  If a commit, then checks out that specific commit.')
                stringParam('GitRepoUrl', Utilities.calculateGitURL(_project), 'Git repo to clone.')
                stringParam('GitRefSpec', '+refs/pull/*:refs/remotes/origin/pr/*', 'RefSpec.  WHEN SUBMITTING PRIVATE JOB FROM YOUR OWN REPO, CLEAR THIS FIELD (or it won\'t find your code)')
                stringParam('DOTNET_CLI_TELEMETRY_PROFILE', "IsInternal_CIServer;${_project}", 'This is used to differentiate the internal CI usage of CLI in telemetry.  This gets exposed in the environment and picked up by the CLI product.')
            }

            definition {
                cpsScm {
                    git {
                        remote {
                            // Sets up the project field to the non-parameterized version
                            github(_project)
                            // Set the refspec to be the parmeterized version
                            refspec('${GitRefSpec}')
                            // Set URL to the parameterized version
                            url('${GitRepoUrl}')

                            if (_credentialsId != null) {
                                credentials(_credentialsId)
                            }
                        }

                        // Set the branch
                        branch('${GitBranchOrCommit}')
                        
                        // Raise the clone timeout
                        extensions {
                            cloneOptions {
                                timeout(30)
                            }
                        }
                    }
                    scriptPath (pipelineFile)
                }
            }
        }
    }

    void emitScmForNonPR(def job, String pipelineFile) {

    }
}