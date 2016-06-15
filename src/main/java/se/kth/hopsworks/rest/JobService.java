package se.kth.hopsworks.rest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.io.IOUtils;
import se.kth.bbc.activity.ActivityFacade;
import se.kth.bbc.fileoperations.FileOperations;
import se.kth.bbc.jobs.jobhistory.Execution;
import se.kth.bbc.jobs.jobhistory.ExecutionFacade;
import se.kth.bbc.jobs.jobhistory.JobType;
import se.kth.bbc.jobs.jobhistory.YarnApplicationAttemptStateFacade;
import se.kth.bbc.jobs.model.configuration.JobConfiguration;
import se.kth.bbc.jobs.model.configuration.ScheduleDTO;
import se.kth.bbc.jobs.model.description.JobDescription;
import se.kth.bbc.jobs.model.description.JobDescriptionFacade;
import se.kth.bbc.project.Project;
import se.kth.hopsworks.controller.JobController;
import se.kth.hopsworks.filters.AllowedRoles;
import se.kth.hopsworks.meta.exception.DatabaseException;

/**
 *
 * @author stig
 */
@RequestScoped
@TransactionAttribute(TransactionAttributeType.NEVER)
public class JobService {

  private static final Logger logger = Logger.getLogger(JobService.class.
      getName());

  @EJB
  private NoCacheResponse noCacheResponse;
  @EJB
  private JobDescriptionFacade jobFacade;
  @EJB
  private ExecutionFacade exeFacade;
  @Inject
  private ExecutionService executions;
  @Inject
  private CuneiformService cuneiform;
  @Inject
  private SparkService spark;
  @Inject
  private AdamService adam;
  @Inject
  private FlinkService flink;
  @EJB
  private FileOperations fops;
  @EJB
  private JobController jobController;
  @EJB
  private YarnApplicationAttemptStateFacade appAttemptStateFacade;
  @EJB
  private ActivityFacade activityFacade;

  private Project project;

  JobService setProject(Project project) {
    this.project = project;
    return this;
  }

  /**
   * Get all the jobs in this project.
   * <p/>
   * @param sc
   * @param req
   * @return A list of all defined Jobs in this project.
   * @throws se.kth.hopsworks.rest.AppException
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_SCIENTIST, AllowedRoles.DATA_OWNER})
  public Response findAllJobs(@Context SecurityContext sc,
      @Context HttpServletRequest req)
      throws AppException {
    List<JobDescription> jobs = jobFacade.findForProject(project);
    GenericEntity<List<JobDescription>> jobList
        = new GenericEntity<List<JobDescription>>(jobs) {
    };
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        jobList).build();
  }

  /**
   * Get the job with the given id in the current project.
   * <p/>
   * @param jobId
   * @param sc
   * @param req
   * @return
   * @throws AppException
   */
  @GET
  @Path("/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getJob(@PathParam("jobId") int jobId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws AppException {
    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return noCacheResponse.
          getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
          "A user is trying to access a job outside their project!");
      return Response.status(Response.Status.FORBIDDEN).build();
    } else {
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
          entity(job).build();
    }
  }

  /**
   * Get the JobConfiguration object for the specified job. The sole reason of existence of this method is the dodginess
   * of polymorphism in JAXB/JAXRS. As such, the jobConfig field is always empty when a JobDescription object is
   * returned. This method must therefore be called explicitly to get the job configuration.
   * <p/>
   * @param jobId
   * @param sc
   * @param req
   * @return
   * @throws AppException
   */
  @GET
  @Path("/{jobId}/config")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getJobConfiguration(@PathParam("jobId") int jobId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws AppException {
    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return noCacheResponse.
          getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
          "A user is trying to access a job outside their project!");
      return Response.status(Response.Status.FORBIDDEN).build();
    } else {
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
          entity(job.getJobConfig()).build();
    }
  }

    /**
   * Get the Job UI url for the specified job
   * <p/>
   * @param jobId
   * @param sc
   * @param req
   * @return url
   * @throws AppException
   */
  @GET
  @Path("/{jobId}/ui")
  @Produces(MediaType.TEXT_PLAIN)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getJobUI(@PathParam("jobId") int jobId,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return noCacheResponse.
              getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
              "A user is trying to access a job outside their project!");
      return Response.status(Response.Status.FORBIDDEN).build();
    } else {
      Execution execution = exeFacade.findForJob(job).get(0);
      Execution updatedExecution = exeFacade.getExecution(execution.getJob().
              getId());
      if (updatedExecution != null) {
        execution = updatedExecution;
      }

      try {
        String trackingUrl = appAttemptStateFacade.findTrackingUrlByAppId(
                execution.getAppId());
        if (trackingUrl != null && trackingUrl != "") {
          trackingUrl = "/hopsworks/api/project/" + project.getId() + "/jobs/"
                  + jobId + "/prox/" + trackingUrl;
          return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
                  entity(trackingUrl).build();
        }
      } catch (Exception e) {
        logger.log(Level.SEVERE, "exception while geting job ui " + e.
                getLocalizedMessage(), e);
      }
      return noCacheResponse.
              getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    }
  }

  private static final HashSet<String> passThroughHeaders = new HashSet<String>(
          Arrays
          .asList("User-Agent", "user-agent", "Accept", "accept",
                  "Accept-Encoding", "accept-encoding", "Accept-Language",
                  "accept-language",
                  "Accept-Charset", "accept-charset"));
  public static final String PROXY_USER_COOKIE_NAME = "proxy-user";

  /**
   * Get the job ui for the specified job. 
   * This act as a proxy to get the job ui from yarn
   * <p/>
   * @param jobId
   * @param sc
   * @param req
   * @return
   * @throws AppException
   */
  @GET
  @Path("/{jobId}/prox/{path: .+}")
  @Produces(MediaType.WILDCARD)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getProx(@PathParam("jobId") int jobId,
          @PathParam("path") String param,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return noCacheResponse.
              getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
              "A user is trying to access a job outside their project!");
      return Response.status(Response.Status.FORBIDDEN).build();
    } else {
      Execution execution = exeFacade.findForJob(job).get(0);
      Execution updatedExecution = exeFacade.getExecution(execution.getJob().
              getId());
      if (updatedExecution != null) {
        execution = updatedExecution;
      }
      String ui = "";
      try {
        logger.log(Level.SEVERE, "get on proxy: " + param);
        String trackingUrl;
//        if (param.matches("([a-z,:,/,.,0-9,-])+:([0-9])+(.)+")) {
          if (param.matches("http([a-z,:,/,.,0-9,-])+:([0-9])+(.)+")) {
            trackingUrl = param;
          } else {
            trackingUrl = "http://" + param;
          }
//        }else {
//          trackingUrl = "http://10.0.2.15:8088/" + param;
//        }
        trackingUrl = trackingUrl.replace("@hwqm", "?");
        logger.log(Level.SEVERE, "get on proxy: " + trackingUrl);
        org.apache.commons.httpclient.URI uri
                = new org.apache.commons.httpclient.URI(trackingUrl, false);
        String source = "http://" + uri.getHost() + ":" + uri.getPort();
        HttpClientParams params = new HttpClientParams();
        params.setCookiePolicy(CookiePolicy.BROWSER_COMPATIBILITY);
        params.setBooleanParameter(HttpClientParams.ALLOW_CIRCULAR_REDIRECTS,
                true);
        HttpClient client = new HttpClient(params);
        HostConfiguration config = new HostConfiguration();
        InetAddress localAddress = InetAddress.getLocalHost();
        config.setLocalAddress(localAddress);

        HttpMethod method = new GetMethod(uri.getEscapedURI());
        Enumeration<String> names = req.getHeaderNames();
        while (names.hasMoreElements()) {
          String name = names.nextElement();
          String value = req.getHeader(name);
          if (passThroughHeaders.contains(name)) {
            //yarn does not send back the js if encoding is not accepted
            //but we don't want to accept encoding for the html because we
            //need to be able to parse it
            if (!name.toLowerCase().equals("accept-encoding") || trackingUrl.
                    contains(".js")) {
              method.setRequestHeader(name, value);
            }
          }
        }
        String user = req.getRemoteUser();
        if (user != null && !user.isEmpty()) {
          method.setRequestHeader("Cookie", PROXY_USER_COOKIE_NAME + "="
                  + URLEncoder.encode(user, "ASCII"));
        }

        client.executeMethod(config, method);
        Response.ResponseBuilder response = noCacheResponse.
                getNoCacheResponseBuilder(Response.Status.OK);
        for (Header header : method.getResponseHeaders()) {
          response.header(header.getName(), header.getValue());
        }

        if (method.getResponseHeader("Content-Type") != null && method.
                getResponseHeader("Content-Type").getValue().contains("html")) {

          ui = method.getResponseBodyAsString();

          //remove the link to the full cluster information in the yarn ui
          ui = ui.replaceAll(
                  "<div id=\"user\">[\\s\\S]+Logged in as: dr.who[\\s\\S]+<div id=\"logo\">",
                  "<div id=\"logo\">");
          ui = ui.replaceAll(
                  "<div id=\"footer\" class=\"ui-widget\">[\\s\\S]+<tbody>",
                  "<tbody>");
          ui = ui.replaceAll("<td id=\"navcell\">[\\s\\S]+<td ", "<td ");
          
          //when geting the logs the file can be very big, we don't want to go 
          //through all of it.
          Header header = method.getResponseHeader("Content-Length");
          if (header != null && Integer.parseInt(header.getValue()) < 100000) {
            String[] elems = ui.split("<");
            ui = "";
            for (String elem : elems) {
              if (elem.equals("")) {
                continue;
              }
              if (elem.contains("href=") || elem.
                      contains("src=")) {
                String[] subElems = elem.split(" ");
                elem = "";
                for (String subElem : subElems) {
                  if (subElem.contains("href=\"//")) {
                    subElem = subElem.replace("href=\"/",
                            "href=\"/hopsworks/api/project/"
                            + project.getId() + "/jobs/" + jobId + "/prox");
                  } else if (subElem.contains("href=\"/")) {
                    subElem = subElem.replace("href=\"",
                            "href=\"/hopsworks/api/project/"
                            + project.getId() + "/jobs/" + jobId + "/prox/"
                            + source);
                  } else if (subElem.contains("href=\"http")) {
                    subElem = subElem.replace("href=\"",
                            "href=\"/hopsworks/api/project/"
                            + project.getId() + "/jobs/" + jobId + "/prox/");
                  } else if (subElem.contains("href=\"")) {
                    subElem = subElem.replace("href=\"",
                            "href=\"/hopsworks/api/project/"
                            + project.getId() + "/jobs/" + jobId + "/prox/"
                            + param);
                  } else if (subElem.contains("src=\"/")) {
                    subElem = subElem.replace("src=\"",
                            "src=\"/hopsworks/api/project/"
                            + project.getId() + "/jobs/" + jobId + "/prox/"
                            + source);
                  } else if (subElem.contains("src=")) {
                    subElem = subElem.replace("src=\"",
                            "src=\"/hopsworks/api/project/"
                            + project.getId() + "/jobs/" + jobId + "/prox/"
                            + source + "/");
                  }
                  subElem = subElem.replace("?", "@hwqm");
                  elem = elem + subElem + " ";
                }

              }

              ui = ui + "<" + elem;
            }
          }
          response.entity(ui);
          response.header("Content-Length", ui.length());
        } else {
          response.entity(method.getResponseBody());
        }
        return response.build();
      } catch (Exception e) {
        logger.log(Level.SEVERE, "exception while geting job ui " + e.
                getLocalizedMessage(), e);
        return noCacheResponse.
                getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
      }
    }
  }
  
  @GET
  @Path("/template/{type}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getConfigurationTemplate(@PathParam("type") String type,
      @Context SecurityContext sc, @Context HttpServletRequest req) {
    JobConfiguration template = JobConfiguration.JobConfigurationFactory.
        getJobConfigurationTemplate(JobType.valueOf(type));
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
        entity(template).build();
  }

  /**
   * Get all the jobs in this project that have a running execution. The return value is a JSON object, where each job
   * id is a key and the corresponding boolean indicates whether the job is running or not.
   * <p/>
   * @param sc
   * @param req
   * @return
   */
  @GET
  @Path("/running")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getConfigurationTemplate(@Context SecurityContext sc,
      @Context HttpServletRequest req) {
    List<JobDescription> running = jobFacade.getRunningJobs(project);
    List<JobDescription> allJobs = jobFacade.findForProject(project);
    JsonObjectBuilder builder = Json.createObjectBuilder();
    for (JobDescription desc : allJobs) {
      try {
        List<Execution> executions = exeFacade.findForJob(desc);
        if (executions != null && executions.isEmpty() == false) {
          Execution execution = executions.get(0);
          builder.add(desc.getId().toString(), Json.createObjectBuilder()
              .add("running", false)
              .add("state", execution.getState().toString())
              .add("finalStatus", execution.getFinalStatus().toString())
              .add("progress", execution.getProgress())
              .add("duration", execution.getExecutionDuration())
              .add("submissiontime", execution.getSubmissionTime().toString())
          );
        }
      } catch (ArrayIndexOutOfBoundsException e) {
        logger.log(Level.WARNING, "No execution was found: " + e
            .getMessage());
      }
    }
    for (JobDescription desc : running) {
      try {
        Execution execution = exeFacade.findForJob(desc).get(0);
        Execution updatedExecution = exeFacade.getExecution(execution.getJob().getId());
        if (updatedExecution != null) {
          execution = updatedExecution;
        }
        String trackingUrl = appAttemptStateFacade.findTrackingUrlByAppId(execution.getAppId());
        builder.add(desc.getId().toString(), 
            Json.createObjectBuilder()
                .add("running", true)
                .add("state", execution.getState().toString())
                .add("finalStatus", execution.getFinalStatus().toString())
                .add("progress", execution.getProgress())
                .add("duration", execution.getExecutionDuration())
                .add("submissiontime", execution.getSubmissionTime().toString())
                .add("url",trackingUrl)
                );
      } catch (ArrayIndexOutOfBoundsException e) {
        logger.log(Level.WARNING, "No execution was found: " + e
            .getMessage());
      }
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
        entity(builder.build()).build();
  }

  /**
   * Get the log information related to a job. The return value is a JSON object, with format logset=[{"time":"JOB
   * EXECUTION TIME"}, {"log":"INFORMATION LOG"}, {"err":"ERROR LOG"}]
   * <p/>
   * @param jobId
   * @param sc
   * @param req
   * @return
   */
  @GET
  @Path("/{jobId}/showlog")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response getLogInformation(@PathParam("jobId") int jobId, @Context SecurityContext sc,
      @Context HttpServletRequest req) {

    JsonObjectBuilder builder = Json.createObjectBuilder();
    JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
    try {
      List<Execution> executionHistory = exeFacade.findbyProjectAndJobId(project, jobId);
      JsonObjectBuilder arrayObjectBuilder;
      if (executionHistory != null && !executionHistory.isEmpty()) {
        String message;
        for (Execution e : executionHistory) {
          arrayObjectBuilder = Json.createObjectBuilder();
          arrayObjectBuilder.add("time", e.getSubmissionTime().toString());
          String hdfsLogPath = "hdfs://" + e.getStdoutPath();
          if (e.getStdoutPath() != null && !e.getStdoutPath().isEmpty() && fops.exists(hdfsLogPath)) {
            message = IOUtils.toString(fops.getInputStream(hdfsLogPath), "UTF-8");
            arrayObjectBuilder.add("log", message.isEmpty() ? "No information." : message);
          } else {
            arrayObjectBuilder.add("log", "No log available");
          }
          String hdfsErrPath = "hdfs://" + e.getStderrPath();
          if (e.getStderrPath() != null && !e.getStderrPath().isEmpty() && fops.exists(hdfsErrPath)) {
            message = IOUtils.toString(fops.getInputStream(hdfsErrPath), "UTF-8");
            arrayObjectBuilder.add("err", message.isEmpty() ? "No error." : message);
          } else {
            arrayObjectBuilder.add("err", "No error log available");
          }
          arrayBuilder.add(arrayObjectBuilder);
        }
      } else {
        arrayObjectBuilder = Json.createObjectBuilder();
        arrayObjectBuilder.add("time", "No log available");
        arrayObjectBuilder.add("log", "No log available");
        arrayObjectBuilder.add("err", "No log available");
        arrayBuilder.add(arrayObjectBuilder);
      }
      builder.add("logset", arrayBuilder);
    } catch (IOException ex) {
      logger.log(Level.WARNING, "Error when reading hdfs logs: {0}", ex.getMessage());
    }

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
        entity(builder.build()).build();
  }
 
  /**
   * Delete the job associated to the project and jobid. The return value is a JSON object stating operation successful
   * or not.
   * <p/>
   * @param jobId
   * @param sc
   * @param req
   * @return
   * @throws se.kth.hopsworks.rest.AppException
   */
  @DELETE
  @Path("/{jobId}/deleteJob")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER})
  public Response deleteJob(@PathParam("jobId") int jobId, @Context SecurityContext sc,
      @Context HttpServletRequest req) throws AppException {
    logger.log(Level.INFO, "Request to delete job");

    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return noCacheResponse.
          getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
          "A user is trying to access a job outside their project!");
      return noCacheResponse.
          getNoCacheResponseBuilder(Response.Status.FORBIDDEN).build();
    } else {
      try {
        logger.log(Level.INFO, "Request to delete job name ={0} job id ={1}", new Object[]{job.getName(), job.getId()});
        jobFacade.removeJob(job);
        logger.log(Level.INFO, "Deleted job name ={0} job id ={1}", new Object[]{job.getName(), job.getId()});
        JsonResponse json = new JsonResponse();
        json.setSuccessMessage("Deleted job " + job.getName() + " successfully");
        activityFacade.persistActivity(ActivityFacade.DELETED_JOB + job.getName(), project, sc.getUserPrincipal().getName());
        return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json).build();
      } catch (DatabaseException ex) {
        logger.log(Level.WARNING, "Job cannot be deleted  job name ={0} job id ={1}", new Object[]{job.getName(), job.getId()});
        throw new AppException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage());
      }
    }
  }

  /**
   * Get the ExecutionService for the job with given id.
   * <p/>
   * @param jobId
   * @return
   */
  @Path("/{jobId}/executions")
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public ExecutionService executions(@PathParam("jobId") int jobId) {
    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return null;
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
          "A user is trying to access a job outside their project!");
      return null;
    } else {
      return this.executions.setJob(job);
    }
  }

  @POST
  @Path("/updateschedule/{jobId}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public Response updateSchedule(ScheduleDTO schedule, @PathParam("jobId") int jobId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws AppException {
    JobDescription job = jobFacade.findById(jobId);
    if (job == null) {
      return noCacheResponse.
          getNoCacheResponseBuilder(Response.Status.NOT_FOUND).build();
    } else if (!job.getProject().equals(project)) {
      //In this case, a user is trying to access a job outside its project!!!
      logger.log(Level.SEVERE,
          "A user is trying to access a job outside their project!");
      return noCacheResponse.
          getNoCacheResponseBuilder(Response.Status.FORBIDDEN).build();
    } else {
      try {
        boolean isScheduleUpdated = jobFacade.updateJobSchedule(jobId, schedule);
        if (isScheduleUpdated) {
          boolean status = jobController.scheduleJob(jobId);
          if (status) {
            JsonResponse json = new JsonResponse();
            json.setSuccessMessage("Scheduled job " + job.getName() + " successfully");
            activityFacade.persistActivity(ActivityFacade.SCHEDULED_JOB + job.getName(), project, sc.getUserPrincipal().getName());
            return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json).build();
          } else {
            logger.log(Level.WARNING, "Schedule is not created in the scheduler for the jobid " + jobId);
          }
        } else {
          logger.log(Level.WARNING, "Schedule is not updated in DB for the jobid " + jobId);
        }

      } catch (DatabaseException ex) {
        logger.log(Level.WARNING, "Cannot update schedule " + ex.getMessage());
        throw new AppException(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), ex.getMessage());
      }
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.INTERNAL_SERVER_ERROR).build();
  }

  @Path("/cuneiform")
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public CuneiformService cuneiform() {
    return this.cuneiform.setProject(project);
  }

  @Path("/spark")
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public SparkService spark() {
    return this.spark.setProject(project);
  }

  @Path("/adam")
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public AdamService adam() {
    return this.adam.setProject(project);
  }

  @Path("/flink")
  @AllowedRoles(roles = {AllowedRoles.DATA_OWNER, AllowedRoles.DATA_SCIENTIST})
  public FlinkService flink() {
    return this.flink.setProject(project);
  }
}
