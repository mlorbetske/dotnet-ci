package org.dotnet.ci.pipelines;

import hudson.Util;
import org.apache.commons.lang.StringUtils;

import jobs.generation.Utilities
import jobs.generation.GenerationSettings

import org.dotnet.ci.triggers.GithubTriggerBuilder
import org.dotnet.ci.triggers.GenericTriggerBuilder
import org.dotnet.ci.triggers.TriggerBuilder
import org.dotnet.ci.pipelines.scm.PipelineScm
import org.dotnet.ci.pipelines.scm.GithubPipelineScm

// Contains functionality to deal with Jenkins pipelines.
// This class enables us to inform Jenkins about pipelines and set up triggers for those pipeline/parameter combos
// as needed.
class Pipeline {
    private String _pipelineFile
    private String _baseJobName
    PipelineScm _scm

    // Context of the Job DSL to use for creating jobs
    private def _context

    private Pipeline(def context, String baseJobName, String pipelineFile) {
        _pipelineFile = pipelineFile
        _baseJobName = baseJobName
        _context = context
    }

    public setSourceControl(PipelineScm scm) {
        _scm = scm
    }

    private static String getDefaultPipelineJobBaseName(String pipelineFile) {
        // Strip off anything after a .
        int lastDot = pipelineFile.indexOf('.')

        if (lastDot != -1) {
            // Has extension
            assert lastDot != 0
            return pipelineFile.substring(0, lastDot)
        }
        else {
            // No extension
            return pipelineFile
        }
    }

    // Replace all the unsafe characters in the input string
    // with _
    // See Jenkins.java's checkGoodName for source of the bad characters
    private String getValidJobNameString(String input) {
        String finalString = ''
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i)
            // Temporary.  Equals is not invalid, but causes problems with CLI
            if('=?*/\\%!@#$^&|<>[]:;'.indexOf("${ch}")!=-1) {
                finalString += '_'
            }
            else {
                finalString += ch
            }
        }

        final int maxElementLength = 64
        return shortenString(finalString, maxElementLength)
    }

    private String shortenString(String input, int max) {
        if (input.length() < max) {
            return input
        }
        
        String abbreviatedInput = StringUtils.abbreviate(input, 0, 16)
        String digest = Util.getDigestOf(input.substring(0, 8))

        // Don't abbreviate if the name would be longer than the original
        if (input.length() < abbreviatedInput.length() + digest.length()) {
            return input
        }
        else {
            return abbreviatedInput + digest
        }
    }

    // Determines a full job name for a pipeline job from the base job and parameter set
    // 
    private String getPipelineJobName(Map<String,String> parameters = [:]) {
        // Take the base job name and append '-'' if there are any parameters
        // If parameters, walk the parameter list.  Append X=Y forms, replacing any
        // invalid characters with _, separated by comma

        String finalJobName = _baseJobName

        if (parameters.size() != 0) {
            finalJobName += '-'
            boolean needsComma = false
            parameters.each { k,v ->
                if (needsComma) {
                    finalJobName += ','
                }
                String paramName = getValidJobNameString(k)
                String paramValue = getValidJobNameString(v)

                // Temporary - Don't use an equals sign.  This causes issues with the CLI
                // finalJobName += "${paramName}=${paramValue}"
                finalJobName += "${paramName}_${paramValue}"
                needsComma = true
            }
        }

        // Shorten the entire job name
        final int maxElementLength = 256
        return shortenString(finalJobName, maxElementLength)
    }

    // Creates a new pipeline given the pipeline groovy script that
    // will be invoked.  A base job name is derived from the pipeline file name
    // Parameters:
    //  context - Context used to construct new pipelines.  Pass 'this' from groovy file.
    //  project - GitHub project that the pipeline lives in.
    //  branch - Branch that the project lives in
    //  pipelineFile - File name relative to root of the repo
    public static Pipeline createPipelineForGithub(def context, String project, String branch, String pipelineFile) {
        String baseName = getDefaultPipelineJobBaseName(pipelineFile)
        return createPipelineForGitHub(context, project, branch, pipelineFile, baseName)
    }

    // Creates a new pipeline given the pipeline groovy script that
    // will be invoked and a base name for the jobs that will be created
    // Parameters:
    //  context - Context used to construct new pipelines.  Pass 'this' from groovy file.
    //  project - GitHub project that the pipeline lives in.
    //  branch - Branch that the project lives in
    //  pipelineFile - File name relative to root of the repo
    //  baseJobName - Jobs that invoke the pipeline will be created with this base name
    public static Pipeline createPipelineForGitHub(def context, String project, String branch, String pipelineFile, String baseJobName) {
        def newPipeline = new Pipeline(context, baseJobName, pipelineFile)

        // Create a new source control for the basic setup here
        def scm = new GithubPipelineScm(project, branch)
        newPipeline.setSourceControl(scm)
        return newPipeline
    }

    // Triggers a puipeline on every Github PR.
    // Parameters:
    //  context - The context that appears for the status check in the Github UI
    //  parameter - Optional set of key/value pairs of string parameters that will be passed to the pipeline
    public def triggerPipelineOnEveryGithubPR(String context, Map<String,String> parameters = [:]) {
        // Create the default trigger phrase based on the context
        return triggerPipelineOnEveryGithubPR(context, null, parameters)
    }

    // Triggers a puipeline on every Github PR, with a custom trigger phrase.
    // Parameters:
    //  context - The context that appears for the status check in the Github UI
    //  triggerPhrase - The trigger phrase that can relaunch the pipeline
    //  parameters - Optional set of key/value pairs of string parameters that will be passed to the pipeline
    public def triggerPipelineOnEveryGithubPR(String context, String triggerPhrase, Map<String,String> parameters = [:]) {
        // Create a trigger builder and pass it to the generic triggerPipelineOnEvent
        GithubTriggerBuilder builder = GithubTriggerBuilder.triggerOnPullRequest()
        builder.setGithubContext(context)
        // If the trigger phrase is non-null, specify it
        if (triggerPhrase != null) {
            builder.setCustomTriggerPhrase(triggerPhrase)
        }
        // Ensure it's always run
        builder.triggerByDefault()
        // Set the target branch
        builder.triggerForBranch(this._scm.getBranch())

        // Call the generic API
        return triggerPipelineOnEvent(builder, parameters)
    }

    // Triggers a pipeline on a Github PR when the specified phrase is commented.
    // Parameters:
    //  context - The context that appears for the status check in the Github UI
    //  triggerPhrase - The trigger phrase that can relaunch the pipeline
    //  parameters - Optional set of key/value pairs of string parameters that will be passed to the pipeline
    public def triggerPipelineOnGithubPRComment(String context, String triggerPhrase, Map<String,String> parameters = [:]) {
        // Create the trigger event and call the helper API
        GithubTriggerBuilder builder = GithubTriggerBuilder.triggerOnPullRequest()
        builder.setGithubContext(context)
        if (triggerPhrase != null) {
            builder.setCustomTriggerPhrase(triggerPhrase)
        }
        builder.triggerOnlyOnComment()
        builder.triggerForBranch(this._scm.getBranch())

        // Call the generic API
        return triggerPipelineOnEvent(builder, parameters)
    }

    // Triggers a pipeline on a Github PR, using the context as the trigger phrase
    // Parameters:
    //  context - The context to show on GitHub + trigger phrase that will launch the job
    // Returns:
    //  Newly created pipeline job
    public def triggerPipelineOnGithubPRComment(String context, Map<String,String> parameters = [:]) {
        // Create the default trigger phrase based on the context
        return triggerPipelineOnGithubPRComment(context, null, parameters)
    }

    // Triggers a pipeline on a Github Push
    // Parameters:
    //  parameters - Parameters to pass to the pipeline on a push
    // Returns:
    //  Newly created job
    public def triggerPipelineOnGithubPush(Map<String,String> parameters = [:]) {
        GithubTriggerBuilder builder = GithubTriggerBuilder.triggerOnCommit()

        // Call the generic API
        return triggerPipelineOnEvent(builder, parameters)
    }

    // Triggers a pipeline periodically, if changes have been made to the
    // source control in question.
    public def triggerPipelinePeriodically(String cronString, Map<String,String> parameters = [:]) {
        GenericTriggerBuilder builder = GenericTriggerBuilder.triggerPeriodically(cronString)

        // Call the generic API
        return triggerPipelineOnEvent(builder, parameters)
    }

    // Creates a pipeline job for a generic trigger event
    // Parameters:
    //  triggerBuilder - Trigger that the pipeline should run on
    //  parameter - Parameter set to run the pipeline with
    // Returns
    //  Newly created pipeline job
    public def triggerPipelineOnEvent(TriggerBuilder triggerBuilder, Map<String,String> params = [:]) {
        // Determine the job name
        // Job name is based off the parameters 

        def isPR = triggerBuilder.isPRTrigger()
        def jobName = getPipelineJobName(params)
        def fullJobName = Utilities.getFullJobName(jobName, isPR)

        // Create the standard pipeline job
        def newJob = createStandardPipelineJob(fullJobName, isPR, params)

        if (isPR) {
            // Emit the source control
            _scm.emitScmForPR(newJob, this._pipelineFile)
        }
        else {
            _scm.emitScmForNonPR(newJob, this._pipelineFile)
        }

        newJob.with {
            // Emit additional parameters for the input parameters
            params.each { k,v ->
                parameters {
                    stringParam(k,v, '')
                }
            }
        }

        // Emit the trigger
        triggerBuilder.emitTrigger(newJob)

        return newJob
    }

    private def createStandardPipelineJob(String fullJobName, boolean isPR, Map<String,String> parameters) {
        // Create the new pipeline job
        def newJob = _context.pipelineJob(fullJobName) {}

        // Most options are set up in the pipeline itself.
        // We really only need to set up the retention policy
        Utilities.addStandardOptions(newJob, isPR)

        // Return the new job
        return newJob
    }
}