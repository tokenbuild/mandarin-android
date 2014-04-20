package com.tomclaw.mandarin.main;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.*;
import android.widget.*;
import com.tomclaw.mandarin.R;
import com.tomclaw.mandarin.core.*;
import com.tomclaw.mandarin.im.StatusUtil;
import com.tomclaw.mandarin.im.icq.IcqAccountRoot;
import com.tomclaw.mandarin.main.adapters.AccountsAdapter;
import com.tomclaw.mandarin.main.adapters.RosterDialogsAdapter;
import com.tomclaw.mandarin.main.adapters.StatusSpinnerAdapter;
import com.tomclaw.mandarin.util.SelectionHelper;

import java.util.ArrayList;
import java.util.Collection;

public class MainActivity extends ChiefActivity {

    private static String MARKET_URI = "market://details?id=";
    private static String GOOGLE_PLAY_URI = "http://play.google.com/store/apps/details?id=";

    private AccountsAdapter accountsAdapter;
    private RosterDialogsAdapter dialogsAdapter;
    private ListView dialogsList;

    private CharSequence title;
    private CharSequence drawerTitle;
    private ActionBarDrawerToggle drawerToggle;
    private DrawerLayout drawerLayout;
    private LinearLayout drawerContent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check for start helper needs to be shown.
        if (PreferenceHelper.isShowStartHelper(this)) {
            // This will start
            Intent accountAddIntent = new Intent(this, AccountAddActivity.class);
            accountAddIntent.putExtra(AccountAddActivity.EXTRA_CLASS_NAME, IcqAccountRoot.class.getName());
            accountAddIntent.putExtra(AccountAddActivity.EXTRA_START_HELPER, true);
            overridePendingTransition(0, 0);
            startActivity(accountAddIntent);
            finish();
            return;
        }

        setContentView(R.layout.main_activity);

        final ActionBar bar = getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setHomeButtonEnabled(true);
        bar.setTitle(R.string.dialogs);
        title = getString(R.string.dialogs);
        drawerTitle = getString(R.string.accounts);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        drawerContent = (LinearLayout) findViewById(R.id.left_drawer);

        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout,
                R.drawable.ic_drawer, R.string.dialogs, R.string.accounts) {

            /** Called when a drawer has settled in a completely closed state. */
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getActionBar().setTitle(title);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }

            /** Called when a drawer has settled in a completely open state. */
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getActionBar().setTitle(drawerTitle);
                invalidateOptionsMenu(); // creates call to onPrepareOptionsMenu()
            }
        };

        // Set the drawer toggle as the DrawerListener
        drawerLayout.setDrawerListener(drawerToggle);

        // Dialogs list.
        dialogsAdapter = new RosterDialogsAdapter(this, getLoaderManager());
        dialogsList = (ListView) findViewById(R.id.chats_list_view);
        dialogsList.setAdapter(dialogsAdapter);
        dialogsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                int buddyDbId = dialogsAdapter.getBuddyDbId(position);
                Log.d(Settings.LOG_TAG, "Check out dialog with buddy (db id): " + buddyDbId);
                Intent intent = new Intent(MainActivity.this, ChatActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        .putExtra(GlobalProvider.HISTORY_BUDDY_DB_ID, buddyDbId);
                startActivity(intent);
            }
        });
        dialogsList.setMultiChoiceModeListener(new MultiChoiceModeListener());

        dialogsList.setEmptyView(findViewById(android.R.id.empty));

        // Accounts list.
        ListView accountsList = (ListView) findViewById(R.id.accounts_list_view);
        // Creating adapter for accounts list
        accountsAdapter = new AccountsAdapter(this, getLoaderManager());
        accountsAdapter.setOnAvatarClickListener(new AccountsAdapter.OnAvatarClickListener() {
            @Override
            public void onAvatarClicked(int accountDbId) {
                // Account is online and we can show it's brief info.
                final AccountInfoTask accountInfoTask =
                        new AccountInfoTask(MainActivity.this, accountDbId);
                TaskExecutor.getInstance().execute(accountInfoTask);
                closeProfilePanel();
            }
        });
        // Bind to our new adapter.
        accountsList.setAdapter(accountsAdapter);
        accountsList.setMultiChoiceModeListener(new AccountsMultiChoiceModeListener());
        accountsList.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Cursor cursor = accountsAdapter.getCursor();
                if (cursor != null && cursor.moveToPosition(position)) {
                    final int accountDbId = cursor.getInt(cursor.getColumnIndex(GlobalProvider.ROW_AUTO_ID));
                    final String accountType = cursor.getString(cursor.getColumnIndex(GlobalProvider.ACCOUNT_TYPE));
                    final String userId = cursor.getString(cursor.getColumnIndex(GlobalProvider.ACCOUNT_USER_ID));
                    final int statusIndex = cursor.getInt(cursor.getColumnIndex(GlobalProvider.ACCOUNT_STATUS));
                    final int accountConnecting = cursor.getInt(cursor.getColumnIndex(GlobalProvider.ACCOUNT_CONNECTING));

                    // Checking for account is connecting now and we must wait for some time.
                    if (accountConnecting == 1) {
                        int toastMessage;
                        if (statusIndex == StatusUtil.STATUS_OFFLINE) {
                            toastMessage = R.string.account_shutdowning;
                        } else {
                            toastMessage = R.string.account_connecting;
                        }
                        Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show();
                    } else {
                        // Checking for account is offline and we need to connect.
                        if (statusIndex == StatusUtil.STATUS_OFFLINE) {
                            View connectDialog = getLayoutInflater().inflate(R.layout.connect_dialog, null);
                            final Spinner statusSpinner = (Spinner) connectDialog.findViewById(R.id.status_spinner);

                            final StatusSpinnerAdapter spinnerAdapter = new StatusSpinnerAdapter(
                                    MainActivity.this, accountType, StatusUtil.getConnectStatuses(accountType));
                            statusSpinner.setAdapter(spinnerAdapter);

                            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                            builder.setTitle(R.string.connect_account_title);
                            builder.setMessage(R.string.connect_account_message);
                            builder.setView(connectDialog);
                            builder.setPositiveButton(R.string.connect_yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        int selectedStatusIndex = spinnerAdapter.getStatus(
                                                statusSpinner.getSelectedItemPosition());
                                        // Trying to connect account.
                                        getServiceInteraction().updateAccountStatusIndex(
                                                accountType, userId, selectedStatusIndex);
                                    } catch (RemoteException ignored) {
                                        // Heh... Nothing to do in this case.
                                        Toast.makeText(MainActivity.this, R.string.unable_to_connect_account,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                            builder.setNegativeButton(R.string.connect_no, null);
                            builder.show();
                        } else {
                            // Account is online and we can show it's brief info.
                            final AccountInfoTask accountInfoTask =
                                    new AccountInfoTask(MainActivity.this, accountDbId);
                            TaskExecutor.getInstance().execute(accountInfoTask);
                            closeProfilePanel();
                        }
                    }
                }
            }
        });
        Button settingsButton = (Button) findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeProfilePanel();
                openSettings();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }
        switch (item.getItemId()) {
            case R.id.accounts: {
                Intent intent = new Intent(this, AccountsActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.create_dialog: {
                Intent intent = new Intent(this, RosterActivity.class);
                startActivity(intent);
                return true;
            }
            case R.id.settings: {
                openSettings();
                return true;
            }
            case R.id.rate_application: {
                rateApplication();
                return true;
            }
            case R.id.info: {
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public void onCoreServiceReady() {
    }

    @Override
    public void onCoreServiceDown() {
        Log.d(Settings.LOG_TAG, "onCoreServiceDown");
    }

    @Override
    public void setTitle(CharSequence title) {
        this.title = title;
        getActionBar().setTitle(title);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    public void onCoreServiceIntent(Intent intent) {
        Log.d(Settings.LOG_TAG, "onCoreServiceIntent");
    }

    private void closeProfilePanel() {
        drawerLayout.closeDrawers();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private class MultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

        private SelectionHelper<Integer, Integer> selectionHelper;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            selectionHelper.onStateChanged(position, (int) id, checked);
            mode.setTitle(String.format(getString(R.string.selected_items), selectionHelper.getSelectedCount()));
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            // Create selection helper to store selected messages.
            selectionHelper = new SelectionHelper<Integer, Integer>();
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            // Assumes that you have menu resources
            inflater.inflate(R.menu.chat_list_edit_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;  // Return false if nothing is done.
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.close_chat_menu: {
                    try {
                        QueryHelper.modifyDialogs(getContentResolver(), selectionHelper.getSelectedIds(), false);
                    } catch (Exception ignored) {
                        // Nothing to do in this case.
                    }
                    break;
                }
                case R.id.select_all_chats_menu: {
                    for (int c = 0; c < dialogsAdapter.getCount(); c++) {
                        dialogsList.setItemChecked(c, true);
                    }
                    return false;
                }
                default: {
                    return false;
                }
            }
            mode.finish();
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionHelper.clearSelection();
        }
    }

    private void rateApplication() {
        final String appPackageName = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(MARKET_URI + appPackageName)));
        } catch (android.content.ActivityNotFoundException ignored) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(GOOGLE_PLAY_URI + appPackageName)));
        }
    }

    private class AccountsMultiChoiceModeListener implements AbsListView.MultiChoiceModeListener {

        private SelectionHelper<Integer, Integer> selectionHelper;

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            selectionHelper.onStateChanged(position, (int) id, checked);
            mode.setTitle(String.format(getString(R.string.selected_items), selectionHelper.getSelectedCount()));
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            selectionHelper = new SelectionHelper<Integer, Integer>();
            // Inflate a menu resource providing context menu items
            MenuInflater inflater = mode.getMenuInflater();
            // Assumes that you have menu resources
            inflater.inflate(R.menu.accounts_edit_menu, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.remove_account_menu:
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle(R.string.remove_accounts_title);
                    builder.setMessage(R.string.remove_accounts_text);
                    builder.setPositiveButton(R.string.yes_remove, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Collection<Integer> selectedAccounts = new ArrayList<Integer>(selectionHelper.getSelectedIds());
                            AccountsRemoveTask task = new AccountsRemoveTask(MainActivity.this, selectedAccounts);
                            TaskExecutor.getInstance().execute(task);
                            // Action picked, so close the CAB
                            mode.finish();
                        }
                    });
                    builder.setNegativeButton(R.string.do_not_remove, null);
                    builder.show();
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            selectionHelper.clearSelection();
        }
    }
}
