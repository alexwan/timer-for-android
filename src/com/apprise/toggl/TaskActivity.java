package com.apprise.toggl;

import java.util.Calendar;
import java.util.Date;
import java.util.Timer;

import com.apprise.toggl.storage.DatabaseAdapter;
import com.apprise.toggl.storage.DatabaseAdapter.PlannedTasks;
import com.apprise.toggl.storage.DatabaseAdapter.Projects;
import com.apprise.toggl.storage.DatabaseAdapter.Tags;
import com.apprise.toggl.storage.models.PlannedTask;
import com.apprise.toggl.storage.models.Task;
import com.apprise.toggl.tracking.TimeTrackingService;
import com.apprise.toggl.widget.NumberPicker;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class TaskActivity extends ApplicationActivity {

  public static final String TASK_ID = "TASK_ID";
  private static final String TAG = "TaskActivity";

  private static final int DATE_DIALOG_ID = 0;
  static final int CREATE_NEW_PROJECT_REQUEST = 1;

  private DatabaseAdapter dbAdapter;
  private TimeTrackingService trackingService;
  private Task task;
  private Button timeTrackingButton;
  private TextView durationView;
  private EditText descriptionView;
  private TextView projectView;
  private TextView dateView;
  private TextView plannedTasksView;
  private TextView tagsView;

  private CheckBox billableCheckBox;

  Timer timer;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.task);

    dbAdapter = new DatabaseAdapter(this, (Toggl) getApplication());
    dbAdapter.open();
    long _id = getIntent().getLongExtra(TASK_ID, -1);
    if (_id > 0) {
      task = dbAdapter.findTask(_id);
    } else {
      task = dbAdapter.createDirtyTask();
    }

    Intent intent = new Intent(this, TimeTrackingService.class);
    if (!TimeTrackingService.isAlive()) {
      startService(intent);
    }
    bindService(intent, trackingConnection, BIND_AUTO_CREATE);

    initViews();
    attachEvents();
  }

  @Override
  protected void onStart() {
    IntentFilter filter = new IntentFilter(
        TimeTrackingService.BROADCAST_SECOND_ELAPSED);
    registerReceiver(updateReceiver, filter);
    super.onStart();
  }

  @Override
  protected void onStop() {
    unregisterReceiver(updateReceiver);
    super.onStop();
  }

  @Override
  protected void onResume() {
    super.onResume();    
    billableCheckBox.setChecked(task.billable);
    updateDescriptionView();
    updateProjectView();
    updatePlannedTasks();
    updateDuration();
    updateDateView();
    updateTagsView();
  }

  @Override
  protected void onPause() {
    saveTask();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    dbAdapter.close();
    unbindService(trackingConnection);
    super.onDestroy();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.task_menu, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
    case R.id.task_menu_new_task:
      Intent intent = new Intent(this, TaskActivity.class);
      startActivity(intent);
      finish();
      return true;
    case R.id.task_menu_delete_task:
      showDeleteTaskDialog();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  protected void initViews() {
    timeTrackingButton = (Button) findViewById(R.id.timer_trigger);
    durationView = (TextView) findViewById(R.id.task_timer_duration);
    descriptionView = (EditText) findViewById(R.id.task_description);
    dateView = (TextView) findViewById(R.id.task_date);
    projectView = (TextView) findViewById(R.id.task_project);
    plannedTasksView = (TextView) findViewById(R.id.task_planned_tasks);
    tagsView = (TextView) findViewById(R.id.task_tags);
    billableCheckBox = (CheckBox) findViewById(R.id.task_billable_cb);
  }

  private void updateProjectView() {
    if (task.project != null) {
      projectView.setText(task.project.client_project_name);
    } else {
      projectView.setText(R.string.choose_tip);
    }
  }

  private void updateDescriptionView() {
    descriptionView.setText(task.description);
  }  

  private void updateDateView() {
    dateView.setText(Util.smallDateString(Util.parseStringToDate(task.start)));
  }

  private void updateTagsView() {
    tagsView.setText(Util.joinStringArray(task.tag_names, ", "));
  }

  private void updatePlannedTasks() {
    if (task.project != null) {
      long project_remote_id = task.project.id;
      Cursor cursor = dbAdapter.findPlannedTasksByProjectId(project_remote_id);
      if ((cursor == null) || (cursor.getCount() == 0) || !cursor.moveToFirst()) {
        plannedTasksView.setVisibility(LinearLayout.GONE);
      }
      if (cursor != null) {
        cursor.close();
      }
    } else {
      plannedTasksView.setVisibility(LinearLayout.GONE);
    }
  }

  protected void attachEvents() {
    timeTrackingButton.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        boolean startTracking = false;
        if (trackingService.isTracking(task)) {
          trackingService.stopTracking();
          task.stop = Util.formatDateToString(Util.currentDate());
          saveTask();
          timeTrackingButton.setBackgroundResource(R.drawable.timer_trigger_button);
        } else if (trackingService.isTracking()) {
          // is tracking another task, stop it and save
          Task currentlyTracked = trackingService.getTrackedTask();
          trackingService.stopTracking();
          currentlyTracked.stop = Util.formatDateToString(Util.currentDate());
          saveTask(currentlyTracked);

          startTracking = true;
        } else {
          startTracking = true;
        }
        
        if (startTracking) {
          trackingService.startTracking(task);
          timeTrackingButton.setBackgroundResource(R.drawable.trigger_active);          
        }
      }
    });

    durationView.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showChooseDurationDialog();
      }
    });

    findViewById(R.id.task_project_area).setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showChooseProjectDialog();
      }
    });

    findViewById(R.id.task_date_area).setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showDialog(DATE_DIALOG_ID);
      }
    });

    billableCheckBox.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        task.billable = billableCheckBox.isChecked();
        saveTask();
      }
    });

    findViewById(R.id.task_tags_area).setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showChooseTagsDialog();
      }
    });

    findViewById(R.id.task_planned_tasks_area).setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        showChoosePlannedTaskDialog();
      }
    });

    descriptionView.setOnKeyListener(new View.OnKeyListener() {
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
          task.description = descriptionView.getText().toString();
        }
        return false;
      }
    });

  }

  private void showChooseProjectDialog() {
    final Cursor projectsCursor = dbAdapter.findAllProjects();
    startManagingCursor(projectsCursor);

    AlertDialog.Builder builder = new AlertDialog.Builder(TaskActivity.this);
    builder.setTitle(R.string.project);

    builder.setSingleChoiceItems(projectsCursor, getCheckedItem(projectsCursor,
        task.project), Projects.CLIENT_PROJECT_NAME,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            projectsCursor.moveToPosition(which);
            long clickedId = projectsCursor.getLong(projectsCursor
                .getColumnIndex(Projects._ID));
            task.project = dbAdapter.findProject(clickedId);
            saveTask();
            updateProjectView();
            updatePlannedTasks();
            dialog.dismiss();
          }
        });
    builder.setPositiveButton(R.string.create_new,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            Intent intent = new Intent(TaskActivity.this,
                CreateProjectActivity.class);
            startActivityForResult(intent, CREATE_NEW_PROJECT_REQUEST);
          }
        });
    builder.setNeutralButton(R.string.no_project,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            task.project = null;
            saveTask();
            updateProjectView();
            updatePlannedTasks();
          }
        });

    builder.show();
  }
  
  private void showChoosePlannedTaskDialog() {
    final Cursor plannedTasksCursor = dbAdapter.findAllPlannedTasks();
    startManagingCursor(plannedTasksCursor);
    
    String[] from = new String[] { PlannedTasks.NAME };
    int[] to = new int[] { R.id.item_name };
    final SimpleCursorAdapter plannedTasksAdapter = new SimpleCursorAdapter(
        TaskActivity.this, R.layout.dialog_list_item, plannedTasksCursor, from, to);    
    
    AlertDialog.Builder builder = new AlertDialog.Builder(TaskActivity.this);
    builder.setTitle(R.string.planned_task);
    
    builder.setAdapter(plannedTasksAdapter, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int pos) {
        long clickedId = plannedTasksAdapter.getItemId(pos);
        PlannedTask plannedTask = dbAdapter.findPlannedTask(clickedId);
        Log.d(TAG, "clicked plannedTask: " + plannedTask);
        task.project = plannedTask.project;
        task.description = plannedTask.name;
        //TODO: should we bind the plannedTask to the task somehow?
        updateProjectView();
        updateDescriptionView();
        saveTask();
      }
    });
    builder.setNegativeButton(R.string.cancel,
        new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
      }
    });
    
    builder.show();
  }

  private void showChooseTagsDialog() {
    final Cursor tagsCursor = dbAdapter.findAllTags();
    startManagingCursor(tagsCursor);
    
    final boolean[] checkedItems = new boolean[tagsCursor.getCount()];
    final String[] items = new String[tagsCursor.getCount()];
    if (tagsCursor.moveToFirst()) {
      // collect all tag names
      for (int i = 0; i < items.length; tagsCursor.moveToNext(), i++) {
        items[i] = tagsCursor.getString(tagsCursor.getColumnIndex(Tags.NAME));
        
        if (task.tag_names != null) {
          // find match
          for (int j = 0; j < task.tag_names.length; j++) {
            if (items[i].equals(task.tag_names[j])) {
              checkedItems[i] = true;
              break;
            }
          }
        }
      }
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(TaskActivity.this);
    builder.setTitle(R.string.tags);

    builder.setMultiChoiceItems(items, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
      public void onClick(DialogInterface dialog, int which,
          boolean isChecked) {
        checkedItems[which] = isChecked;
      }
    });
    builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {
        int count = 0;
        // collect amount of checked tags
        for (int i = 0; i < checkedItems.length; i++) if (checkedItems[i]) count += 1;

        String[] newTagNames = new String[count];
        for (int i = 0, j = 0; i < items.length; i++) {
          if (checkedItems[i]) {
            newTagNames[j++] = items[i];
          }
        }

        task.tag_names = newTagNames;
        saveTask();
        updateTagsView();
      }
    });
    builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int which) {}
    });

    builder.show();
  }

  private void showChooseDurationDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(TaskActivity.this);
    View durationPicker = getLayoutInflater().inflate(R.layout.duration_picker, null);

    final NumberPicker hoursPicker = (NumberPicker) durationPicker
        .findViewById(R.id.picker_duration_hours);
    final NumberPicker minutesPicker = (NumberPicker) durationPicker
        .findViewById(R.id.picker_duration_minutes);
    hoursPicker.setRange(0, 23);
    minutesPicker.setRange(0, 59);

    hoursPicker.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);
    minutesPicker.setFormatter(NumberPicker.TWO_DIGIT_FORMATTER);

    hoursPicker.setCurrent(Util.getHoursFromSeconds(task.duration));
    minutesPicker.setCurrent(Util.getMinutesFromSeconds(task.duration));
    
    builder.setTitle(Util.hoursMinutesSummary(hoursPicker.getCurrent(),
        minutesPicker.getCurrent(), getResources()));
    builder.setView(durationPicker);
    builder.setPositiveButton(R.string.set,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            int hours = hoursPicker.getCurrent();
            int minutes = minutesPicker.getCurrent();
            // only hours and minutes are picker, hence get the
            // seconds from existing task duration
            int seconds = (int) task.duration % 60;

            long duration = (hours * 60 * 60) + (minutes * 60) + seconds;
            task.duration = duration;
            saveTask();
            updateDuration();
            if (trackingService.isTracking(task)) {
              trackingService.setCurrentDuration(duration);
            }
          }
        });
    builder.setNegativeButton(R.string.cancel,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
          }
        });

    final AlertDialog durationDialog = builder.show();

    NumberPicker.OnChangedListener numbersListener = new NumberPicker.OnChangedListener() {
      public void onChanged(NumberPicker picker, int oldVal, int newVal) {
        durationDialog.setTitle(Util.hoursMinutesSummary(hoursPicker
            .getCurrent(), minutesPicker.getCurrent(), getResources()));
      }
    };

    hoursPicker.setOnChangeListener(numbersListener);
    minutesPicker.setOnChangeListener(numbersListener);
  }

  protected void saveTask() {
    saveTask(task);
  }
  
  protected void saveTask(Task task) {
    Log.d(TAG, "saving task: " + task);
    task.sync_dirty = true;
    if (task._id > 0) {
      dbAdapter.updateTask(task);
    } else {
      dbAdapter.createTask(task);
    }
  }

  private void showDeleteTaskDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(TaskActivity.this);
    builder.setTitle(R.string.delete_task);
    builder.setMessage(R.string.are_you_sure);

    builder.setPositiveButton(R.string.yes,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
            dbAdapter.deleteTask(task);
            finish();
          }
        });
    builder.setNegativeButton(R.string.cancel,
        new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog, int which) {
          }
        });

    builder.show();
  }

  private void setDate(int year, int month, int date) {
    Date start = Util.parseStringToDate(task.start);
    Date stop = Util.parseStringToDate(task.stop);

    Calendar cal = (Calendar) Calendar.getInstance().clone();
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month);
    cal.set(Calendar.DATE, date);
    cal.set(Calendar.HOUR_OF_DAY, start.getHours());
    cal.set(Calendar.MINUTE, start.getMinutes());
    cal.set(Calendar.SECOND, start.getSeconds());
    task.start = Util.formatDateToString(cal.getTime());

    cal.set(Calendar.HOUR_OF_DAY, stop.getHours());
    cal.set(Calendar.MINUTE, stop.getMinutes());
    cal.set(Calendar.SECOND, stop.getSeconds());
    task.stop = Util.formatDateToString(cal.getTime());

    saveTask();
    updateDateView();
  }

  private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {

    public void onDateSet(DatePicker view, int year, int month, int date) {
      setDate(year, month, date);
    }
  };

  @Override
  protected Dialog onCreateDialog(int id) {
    switch (id) {
    case DATE_DIALOG_ID:
      Calendar cal = Util.parseStringToCalendar(task.start);
      int mYear = cal.get(Calendar.YEAR);
      int mMonth = cal.get(Calendar.MONTH);
      int mDay = cal.get(Calendar.DATE);
      return new DatePickerDialog(this, mDateSetListener, mYear, mMonth, mDay);
    }
    return null;
  }

  private BroadcastReceiver updateReceiver = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (trackingService.isTracking(task)) {
        task.duration = trackingService.getCurrentDuration();
        updateDuration();
      }
    }

  };

  private ServiceConnection trackingConnection = new ServiceConnection() {

    public void onServiceConnected(ComponentName name, IBinder service) {
      TimeTrackingService.TimeTrackingBinder binding = (TimeTrackingService.TimeTrackingBinder) service;
      trackingService = binding.getService();

      if (trackingService.isTracking(task)) {
        task.duration = trackingService.getCurrentDuration();
        updateDuration();
        timeTrackingButton.setBackgroundResource(R.drawable.trigger_active);
      }
    }

    public void onServiceDisconnected(ComponentName name) {

    }

  };

  private void updateDuration() {
    durationView.setText(Util.secondsToHMS(task.duration));
  }

  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == CREATE_NEW_PROJECT_REQUEST) {
      if (resultCode == RESULT_OK) {
        long createdId = data.getLongExtra(
            CreateProjectActivity.CREATED_PROJECT_LOCAL_ID, 0);
        if (createdId > 0) {
          task.project = dbAdapter.findProject(createdId);
          saveTask();
          updateProjectView();
        }
      }
    }
  }

}
