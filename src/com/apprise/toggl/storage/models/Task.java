package com.apprise.toggl.storage.models;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.apprise.toggl.Util;
import com.apprise.toggl.storage.DatabaseAdapter.Tasks;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


public class Task extends Model {

  public Project project;
  public Workspace workspace;
  public long duration;
  public String start;
  public boolean billable;
  public String description;
  public String stop;
  public String[] tag_names;

  public Task() {}
  
  public Task(long _id, Project project, Workspace workspace, long duration,
      String start, boolean billable, String description, String stop,
      String[] tagNames, long remote_id, boolean syncDirty) {
    this._id = _id;
    this.project = project;
    this.workspace = workspace;
    this.duration = duration;
    this.start = start;
    this.billable = billable;
    this.description = description;
    this.stop = stop;
    this.tag_names = tagNames;
    this.id = remote_id;
    this.sync_dirty = syncDirty;
  }

  public static Task dirty(Project project, Workspace workspace, long duration,
      String start, boolean billable, String description, String stop,
      String[] tagNames, long remote_id) {
    Task task = new Task();
    task.project = project;
    task.workspace = workspace;
    task.duration = duration;
    task.start = start;
    task.billable = billable;
    task.description = description;
    task.stop = stop;
    task.tag_names = tagNames;
    task.id = remote_id;
    task.sync_dirty = true;
    return task;
  }
  
  public String toString() {
    Long project_id = null;
    Long workspace_id = null;
    
    if (project != null)
      project_id = new Long(project._id);
    if (workspace != null)
      workspace_id = new Long(workspace._id);

    String tagNames = Util.joinStringArray(this.tag_names, ";");
    
    return "project_id: " + project_id
      + ", workspace_id: " + workspace_id
      + ", duration: " + duration
      + ", start: " + start
      + ", billable: " + billable
      + ", description: " + description
      + ", remote_id: " + id
      + ", stop: " + stop
      + ", tag_names: " + tagNames;
  }

  public boolean identicalTo(Model other) {
    Task otherTask = (Task) other;
    
    return this.toString().equals(otherTask.toString());
  }
    
  public void updateAttributes(Model other) {
    Task otherTask = (Task) other;
    project = otherTask.project;
    workspace = otherTask.workspace;
    duration = otherTask.duration;
    start = otherTask.start;
    billable = otherTask.billable;
    description = otherTask.description;
    stop = otherTask.stop;
    tag_names = otherTask.tag_names;
  }
  
  public String apiJsonString(User currentUser) {
    Gson gson = new Gson();
    JsonObject workspaceObj = new JsonObject();
    JsonObject projectObj = new JsonObject();
    JsonObject rootObj = new JsonObject();
    JsonObject taskObj = gson.toJsonTree(this).getAsJsonObject();
    
    if (this.project != null) {
      projectObj.addProperty("id", project.id);
      taskObj.add("project", projectObj);
    }
    
    if (this.workspace == null) {
      workspaceObj.addProperty("id", currentUser.default_workspace_id);
    } else {
      workspaceObj.addProperty("id", this.workspace.id);      
    }
    
//    JSONArray tagNamesArr = new JSONArray(this.tag_names);
//    tagNamesArr.join(";");
//    taskObj.addProperty("tag_names", tagNamesObj);
    taskObj.add("workspace", workspaceObj);
    rootObj.add("task", taskObj);
    
    return rootObj.toString();
  }  
    
}
