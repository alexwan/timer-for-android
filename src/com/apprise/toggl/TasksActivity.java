package com.apprise.toggl;

import java.util.Calendar;

import com.apprise.toggl.remote.SyncService;
import com.apprise.toggl.storage.DatabaseAdapter;
import com.apprise.toggl.storage.DatabaseAdapter.Projects;
import com.apprise.toggl.storage.DatabaseAdapter.Tasks;
import com.apprise.toggl.storage.models.User;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class TasksActivity extends ApplicationListActivity {

  private static final String TAG = "TasksActivity"; 
  
  private DatabaseAdapter dbAdapter;
  private SyncService syncService;
  private Toggl app;
  private User currentUser;
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    setProgressBarIndeterminate(true);
    setContentView(R.layout.tasks);
    
    init();
  }
  
  @Override
  protected void onResume() {
    super.onResume();    
    IntentFilter filter = new IntentFilter(SyncService.SYNC_COMPLETED);
    registerReceiver(updateReceiver, filter);
    rebuildList();
  }
  
  @Override
  protected void onPause() {
    unregisterReceiver(updateReceiver);
    super.onPause();
  }
  
  @Override
  protected void onDestroy() {
    unbindService(syncConnection);
    super.onDestroy();
  }

  protected void init() {
    app = (Toggl) getApplication();    
    dbAdapter = new DatabaseAdapter(this, app);
    currentUser = app.getCurrentUser();
    Intent intent = new Intent(this, SyncService.class);
    bindService(intent, syncConnection, BIND_AUTO_CREATE);
  }
  
  public void populateList() {
    Cursor tasksCursor;  
    TasksCursorAdapter cursorAdapter;
    int taskRetentionDays = currentUser.task_retention_days;
    Calendar queryCal = (Calendar) Calendar.getInstance().clone();
    String[] fieldsToShow = { Tasks.DURATION, Tasks.DESCRIPTION, Projects.CLIENT_PROJECT_NAME };
    int[] viewsToFill = { R.id.task_item_duration, R.id.task_item_description, R.id.task_item_client_project_name };
    String date;
    String header_text;
    
    dbAdapter.open();

    for (int i = 0; i <= taskRetentionDays; i++) {
      tasksCursor = dbAdapter.findTasksForListByDate(queryCal.getTime());
      startManagingCursor(tasksCursor);
      cursorAdapter = new TasksCursorAdapter(this, R.layout.task_item,
          tasksCursor, fieldsToShow, viewsToFill);
          date = Util.smallDateString(queryCal.getTime());
      
      header_text = date + " (" + Util.secondsToHM(getDurationTotal(tasksCursor)) + " h)";
      if (cursorAdapter.getCount() != 0)
        adapter.addSection(header_text, cursorAdapter);
      queryCal.add(Calendar.DATE, -1);
    }
    
    setListAdapter(adapter);
    dbAdapter.close();
  }
  
  private void rebuildList() {
    adapter.clearSections();
    populateList();    
  }

  private long getDurationTotal(Cursor tasksCursor) {
    long duration_total = 0;
    long duration;
    while (tasksCursor.moveToNext()) {
      duration = tasksCursor.getLong(tasksCursor.getColumnIndex(Tasks.DURATION));
      if (duration > 0) duration_total += duration; 
    }
    duration = 0;
    return duration_total;
  }
  
  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Intent intent = new Intent(TasksActivity.this, TaskActivity.class);

    Cursor cursor = (Cursor) adapter.getItem(position);
    long clickedTaskId = cursor.getLong(cursor.getColumnIndex(Tasks._ID));
    
    intent.putExtra(TaskActivity.TASK_ID, clickedTaskId);
    startActivity(intent);    
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.tasks_menu, menu);
    return super.onCreateOptionsMenu(menu);
  }   

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.tasks_menu_refresh:
        setProgressBarIndeterminateVisibility(true);
        new Thread(refreshTasksInBackgrond).start();
        return true;
      case R.id.tasks_menu_account:
        startActivity(new Intent(this, AccountActivity.class));
        return true;
    }
    return super.onOptionsItemSelected(item);
  }  
  
  protected ServiceConnection syncConnection = new ServiceConnection() {
    
    public void onServiceDisconnected(ComponentName name) {}
    
    public void onServiceConnected(ComponentName name, IBinder serviceBinding) {
      SyncService.SyncBinder binding = (SyncService.SyncBinder) serviceBinding;
      syncService = binding.getService();
    }

  };
  
  protected Runnable refreshTasksInBackgrond = new Runnable() {
    
    public void run() {
      syncService.syncTasks();
    }
  };
  
  protected BroadcastReceiver updateReceiver = new BroadcastReceiver() {
    
    @Override
    public void onReceive(Context context, Intent intent) {
      rebuildList();
      setProgressBarIndeterminateVisibility(false);      
    }
  };

  SectionedAdapter adapter = new SectionedAdapter() {
    protected View getHeaderView(String caption, int index, View convertView, ViewGroup parent) {
      LinearLayout result = (LinearLayout) convertView;

      if (convertView == null) {
        result = (LinearLayout) getLayoutInflater().inflate(
            R.layout.tasks_group_header, null);

      }

      TextView header = (TextView) result.findViewById(R.id.task_list_header_text);      
      header.setText(caption);

      return (result);
    }
  };
  
  public final class TasksCursorAdapter extends SimpleCursorAdapter {

    public TasksCursorAdapter(Context context, int layout, Cursor c,
        String[] from, int[] to) {
      super(context, layout, c, from, to);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
      TextView durationView = (TextView) view.findViewById(R.id.task_item_duration);
      long seconds = cursor.getLong(cursor.getColumnIndex(Tasks.DURATION));
      durationView.setText(Util.secondsToHMS(seconds));

      TextView descriprionView = (TextView) view.findViewById(R.id.task_item_description);
      String description = cursor.getString(cursor.getColumnIndex(Tasks.DESCRIPTION));
      if (description == null) {
        descriprionView.setText(R.string.no_description);
        descriprionView.setTextColor(R.color.light_gray);
      } else {
        descriprionView.setText(description);  
      }
      
      TextView clientProjectNameView = (TextView) view.findViewById(R.id.task_item_client_project_name);
      String clientProjectName = cursor.getString(cursor.getColumnIndex(Projects.CLIENT_PROJECT_NAME));
      clientProjectNameView.setText(clientProjectName);
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
      View view = getLayoutInflater().inflate(R.layout.task_item, null);
      bindView(view, context, cursor);
      return view;
    }
  }  

}
