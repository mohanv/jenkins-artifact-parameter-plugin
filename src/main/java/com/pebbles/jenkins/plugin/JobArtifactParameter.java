package com.pebbles.jenkins.plugin;

import hudson.Extension;
import hudson.model.ParameterValue;
import hudson.model.TopLevelItem;
import hudson.model.Hudson;
import hudson.model.Job;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.util.RunList;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.logging.Logger;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * String based parameter that supports picking an archived artifact from a selected job prior to 
 * building.  The job from which the artifacts are to be retrieved is stored in the 
 * build configuration.  When the build is run, all the builds for that job is populated.
 * Any of the artifacts for the selected build can be selected and the full path of that
 * artifact is set in that environment variable.
 * 
 * @author Mohan
 * @see {@link ParameterDefinition}
 */

public class JobArtifactParameter extends ParameterDefinition {
	private static final Logger LOG = Logger.getLogger(JobArtifactParameter.class.getName());
	static final long serialVersionUID = 4;
	public String value = "";

	public String jobName;
	public String buildId;
	public String artifact;

	/**
	 * The name and jobName are persisted in the configuration and hence need to be declared
	 * in the constructor
	 * @param name
	 * @param jobName
	 */
	@DataBoundConstructor
	public JobArtifactParameter(String name, String jobName) {
		super(name);
		this.jobName = jobName;
	}

	@Extension
	public static final class DescriptorImpl extends ParameterDescriptor {
		@Override
		public String getDisplayName() {
			//  This will be displayed in build parameter drop down
			return "Artifacts Parameter";
		}

		/**
		 * Helper function to retrieve the name of the current job using the descriptor.
		 * @return
		 */
		private String getThisJobName() {
			String containsJobName = getCurrentDescriptorByNameUrl();
			String jobName;
			try {
				jobName = java.net.URLDecoder.decode(containsJobName.substring(containsJobName.lastIndexOf("/") + 1), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			return jobName;
		}

		/**
		 * Helper function to retrieve the parent parameter object from the descriptor
		 * @param param
		 * @return
		 */
		private JobArtifactParameter getArtifactParameter(String param) {
			String jobName = getThisJobName();
			Job<?, ?> j = Hudson.getInstance().getItemByFullName(jobName, hudson.model.Job.class);
			if (j != null) {
				ParametersDefinitionProperty pdp = j.getProperty(hudson.model.ParametersDefinitionProperty.class);
				List<ParameterDefinition> pds = pdp.getParameterDefinitions();
				for (ParameterDefinition pd : pds) {
					if (this.isInstance(pd) && ((JobArtifactParameter) pd).getName().equalsIgnoreCase(param)) {
						return (JobArtifactParameter) pd;
					}
				}
			}
			LOG.warning("Could not find parameter definition instance for parameter " + param);
			return null;
		}

		/**
		 * Invoked to fill up the job names drop down.  The current job is filtered from the list.
		 * @param name
		 * @return
		 */
		public ListBoxModel doFillJobNameItems(@QueryParameter String name) {
			ListBoxModel m = new ListBoxModel();

			String jobName = getThisJobName();
			Hudson instance = Hudson.getInstance();
			List<TopLevelItem> jobs = instance.getItems();
			for (TopLevelItem job : jobs) {
				if (!jobName.equals(job.getName())) {
					m.add(job.getName());
				}
			}
			return m;
		}

		/**
		 * Invoked to fill up the build drop down.
		 * @param jobName
		 * @return
		 */
		public ListBoxModel doFillBuildIdItems(@QueryParameter String jobName) {
			ListBoxModel m = new ListBoxModel();
			Hudson instance = Hudson.getInstance();
			RunList<Run> t = ((Project) instance.getItem(jobName)).getBuilds().newBuilds();
			for (int i = 0; i < t.size(); i++) {
				m.add(t.get(i).getNumber() + "");
			}
			return m;
		}

		/**
		 * Invoked to fill up the artifact drop down.  This is dependant on the build id drop down.
		 * @param name
		 * @param buildId
		 * @return
		 */
		public ListBoxModel doFillArtifactItems(@QueryParameter String name, @QueryParameter String buildId) {
			ListBoxModel m = new ListBoxModel();
			if (buildId == null || buildId.length() == 0)
				return m;
			JobArtifactParameter dp = getArtifactParameter(name);
			String jobName = dp.jobName;
			Hudson instance = Hudson.getInstance();
			File artifactsDir = ((Project) instance.getItem(jobName)).getBuildByNumber(Integer.parseInt(buildId)).getArtifactsDir();
			List t = ((Project) instance.getItem(jobName)).getBuildByNumber(Integer.parseInt(buildId)).getArtifacts();
			for (int i = 0; i < t.size(); i++) {
				m.add(t.get(i) + "");
			}
			return m;
		}

		/**
		 * Invoked to fill in select box item.  This is hidden.  Using the value item, so that
		 * StringParameterValue can be used
		 * @param name
		 * @param buildId
		 * @param artifact
		 * @return
		 */
		public ListBoxModel doFillValueItems(@QueryParameter String name, @QueryParameter String buildId, @QueryParameter String artifact) {
			ListBoxModel m = new ListBoxModel();
			if (buildId == null || buildId.length() == 0|| artifact.length()==0)
				return m;
			JobArtifactParameter dp = getArtifactParameter(name);
			Hudson instance = Hudson.getInstance();
			File artifactsDir = ((Project) instance.getItem(dp.jobName)).getBuildByNumber(Integer.parseInt(buildId)).getArtifactsDir();
			m.add(artifactsDir.getAbsolutePath() +"/"+ artifact);
			return m;
		}
	}

	/**
	 * This method is invoked when the parameterized build is run.  The parameter as a jsonObject is bound
	 * to the StringParameterValue object
	 * @param req
	 * @param jo
	 * @return
	 */
	@Override
	public ParameterValue createValue(StaplerRequest req, JSONObject jo) {
		//  Using StringParameterValue, make sure the name and value are set in index.jelly
		StringParameterValue value = req.bindJSON(StringParameterValue.class, jo);
		return value;
	}
	/**
	 * @param req
	 * @return
	 */
	@Override
	public ParameterValue createValue(StaplerRequest req) {
		LOG.warning("Unsupported createValue is being invoked!");
		throw new UnsupportedOperationException();
	}
}