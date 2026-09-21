package com.winlator.cmod.winhandler;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.winlator.cmod.R;
import com.winlator.cmod.XServerDisplayActivity;
import com.winlator.cmod.contentdialog.ContentDialog;
import com.winlator.cmod.core.CPUStatus;
import com.winlator.cmod.core.FileUtils;
import com.winlator.cmod.core.ProcessHelper;
import com.winlator.cmod.core.StringUtils;
import com.winlator.cmod.widget.CPUListView;
import com.winlator.cmod.xenvironment.ImageFs;
import com.winlator.cmod.xserver.Window;
import com.winlator.cmod.xserver.XLock;
import com.winlator.cmod.xserver.XServer;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

public class TaskManagerDialog extends ContentDialog implements OnGetProcessInfoListener {

    private final XServerDisplayActivity activity;
    private final LayoutInflater inflater;
    private Timer timer;
    private final Object lock = new Object();

    /*
     * 0 = Auto
     * 1 = 2 GB
     * 2 = 3 GB
     * 3 = 4 GB
     * 4 = 5 GB
     * 5 = 6 GB
     * 6 = 7 GB
     * 7 = 8 GB
     */
    private int selectedMemoryOption = 0;

    private static final String PREFS_NAME = "task_manager_memory";
    private static final String PREF_MEMORY_OPTION = "memory_option";

    public TaskManagerDialog(XServerDisplayActivity activity) {
        super(activity, R.layout.task_manager_dialog);

        this.activity = activity;

        setCancelable(false);
        setTitle(R.string.task_manager);
        setIcon(R.drawable.icon_task_manager);

        /*
         * Carrega a opção salva.
         */
        SharedPreferences preferences = activity.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        selectedMemoryOption = preferences.getInt(
                PREF_MEMORY_OPTION,
                0
        );

        /*
         * Botão New Task.
         */
        Button cancelButton = findViewById(R.id.BTCancel);

        cancelButton.setText(R.string.new_task);

        cancelButton.setOnClickListener((v) -> {
            dismiss();

            ContentDialog.prompt(
                    activity,
                    R.string.new_task,
                    "taskmgr.exe",
                    (command) -> activity.getWinHandler().exec(command)
            );
        });

        /*
         * Quando o Task Manager fecha.
         */
        setOnDismissListener((dialog) -> {

            if (timer != null) {
                timer.cancel();
                timer = null;
            }

            activity.getWinHandler()
                    .setOnGetProcessInfoListener(null);
        });

        FileUtils.clear(getIconDir(activity));

        inflater = LayoutInflater.from(activity);
    }

    private void update() {

        synchronized (lock) {

            activity.getWinHandler().listProcesses();

            final LinearLayout container =
                    findViewById(R.id.LLProcessList);

            if (container.getChildCount() == 0) {

                findViewById(R.id.TVEmptyText)
                        .setVisibility(View.VISIBLE);
            }
        }

        updateCPUInfoView();
        updateMemoryInfoView();
    }

    /*
     * ============================================================
     * MEMORY MENU
     * ============================================================
     */

    private void showMemoryMenu(final View anchorView) {

        PopupMenu memoryMenu =
                new PopupMenu(activity, anchorView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            memoryMenu.setForceShowIcon(false);
        }

        memoryMenu.getMenu().add("Auto");
        memoryMenu.getMenu().add("2 GB");
        memoryMenu.getMenu().add("3 GB");
        memoryMenu.getMenu().add("4 GB");
        memoryMenu.getMenu().add("5 GB");
        memoryMenu.getMenu().add("6 GB");
        memoryMenu.getMenu().add("7 GB");
        memoryMenu.getMenu().add("8 GB");

        memoryMenu.setOnMenuItemClickListener(menuItem -> {

            String value =
                    menuItem.getTitle().toString();

            int option;

            switch (value) {

                case "2 GB":
                    option = 1;
                    break;

                case "3 GB":
                    option = 2;
                    break;

                case "4 GB":
                    option = 3;
                    break;

                case "5 GB":
                    option = 4;
                    break;

                case "6 GB":
                    option = 5;
                    break;

                case "7 GB":
                    option = 6;
                    break;

                case "8 GB":
                    option = 7;
                    break;

                default:
                    option = 0;
                    break;
            }

            selectedMemoryOption = option;

            /*
             * Salva a escolha.
             */
            activity.getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
            )
                    .edit()
                    .putInt(
                            PREF_MEMORY_OPTION,
                            selectedMemoryOption
                    )
                    .apply();

            /*
             * Atualiza imediatamente.
             */
            updateMemoryInfoView();

            return true;
        });

        memoryMenu.show();
    }

    /*
     * Retorna a memória escolhida.
     *
     * 0 = Auto
     * 1 = 2 GB
     * ...
     * 7 = 8 GB
     */
    private long getSelectedMemoryBytes() {

        switch (selectedMemoryOption) {

            case 1:
                return 2L * 1024L * 1024L * 1024L;

            case 2:
                return 3L * 1024L * 1024L * 1024L;

            case 3:
                return 4L * 1024L * 1024L * 1024L;

            case 4:
                return 5L * 1024L * 1024L * 1024L;

            case 5:
                return 6L * 1024L * 1024L * 1024L;

            case 6:
                return 7L * 1024L * 1024L * 1024L;

            case 7:
                return 8L * 1024L * 1024L * 1024L;

            default:
                return 0;
        }
    }

    /*
     * Texto da opção selecionada.
     */
    private String getSelectedMemoryText() {

        switch (selectedMemoryOption) {

            case 1:
                return "2 GB";

            case 2:
                return "3 GB";

            case 3:
                return "4 GB";

            case 4:
                return "5 GB";

            case 5:
                return "6 GB";

            case 6:
                return "7 GB";

            case 7:
                return "8 GB";

            default:
                return "Auto";
        }
    }

    /*
     * ============================================================
     * PROCESS MENU
     * ============================================================
     */

    private void showListItemMenu(
            final View anchorView,
            final ProcessInfo processInfo
    ) {

        PopupMenu listItemMenu =
                new PopupMenu(activity, anchorView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listItemMenu.setForceShowIcon(true);
        }

        listItemMenu.inflate(
                R.menu.process_popup_menu
        );

        listItemMenu.setOnMenuItemClickListener(
                (menuItem) -> {

                    int itemId =
                            menuItem.getItemId();

                    final WinHandler winHandler =
                            activity.getWinHandler();

                    if (itemId == R.id.process_affinity) {

                        showProcessorAffinityDialog(
                                processInfo
                        );

                    } else if (itemId == R.id.bring_to_front) {

                        winHandler.bringToFront(
                                processInfo.name
                        );

                        dismiss();

                    } else if (itemId == R.id.process_end) {

                        ContentDialog.confirm(
                                activity,
                                R.string.do_you_want_to_end_this_process,
                                () -> winHandler.killProcess(
                                        processInfo.name
                                )
                        );
                    }

                    return true;
                }
        );

        listItemMenu.show();
    }

    private void showProcessorAffinityDialog(
            final ProcessInfo processInfo
    ) {

        ContentDialog dialog =
                new ContentDialog(
                        activity,
                        R.layout.cpu_list_dialog
                );

        dialog.setTitle(processInfo.name);
        dialog.setIcon(R.drawable.icon_cpu);

        final CPUListView cpuListView =
                dialog.findViewById(
                        R.id.CPUListView
                );

        cpuListView.setCheckedCPUList(
                processInfo.getCPUList()
        );

        dialog.setOnConfirmCallback(() -> {

            WinHandler winHandler =
                    activity.getWinHandler();

            winHandler.setProcessAffinity(
                    processInfo.pid,
                    ProcessHelper.getAffinityMask(
                            cpuListView.getCheckedCPUList()
                    )
            );

            update();
        });

        dialog.show();
    }

    /*
     * ============================================================
     * ICON DIRECTORY
     * ============================================================
     */

    public static File getIconDir(Context context) {

        File iconDir = new File(
                ImageFs.find(context).getRootDir(),
                "home/xuser/.local/share/icons/taskmgr"
        );

        if (!iconDir.isDirectory()) {
            iconDir.mkdirs();
        }

        return iconDir;
    }

    /*
     * ============================================================
     * SHOW
     * ============================================================
     */

    @Override
    public void show() {

        update();

        activity.getWinHandler()
                .setOnGetProcessInfoListener(this);

        timer = new Timer();

        timer.schedule(
                new TimerTask() {

                    @Override
                    public void run() {

                        activity.runOnUiThread(
                                TaskManagerDialog.this::update
                        );
                    }
                },
                0,
                1000
        );

        super.show();
    }

    /*
     * ============================================================
     * PROCESS INFORMATION
     * ============================================================
     */

    @Override
    public void onGetProcessInfo(
            int index,
            int numProcesses,
            ProcessInfo processInfo
    ) {

        activity.runOnUiThread(() -> {

            synchronized (lock) {

                final LinearLayout container =
                        findViewById(
                                R.id.LLProcessList
                        );

                setBottomBarText(
                        activity.getString(
                                R.string.processes
                        ) + ": " + numProcesses
                );

                if (numProcesses == 0) {

                    container.removeAllViews();

                    findViewById(
                            R.id.TVEmptyText
                    ).setVisibility(View.VISIBLE);

                    return;
                }

                findViewById(
                        R.id.TVEmptyText
                ).setVisibility(View.GONE);

                int childCount =
                        container.getChildCount();

                View itemView =
                        index < childCount
                                ? container.getChildAt(index)
                                : inflater.inflate(
                                        R.layout.process_info_list_item,
                                        container,
                                        false
                                );

                ((TextView) itemView.findViewById(
                        R.id.TVName
                )).setText(
                        processInfo.name +
                                (
                                        processInfo.wow64Process
                                                ? " *32"
                                                : ""
                                )
                );

                ((TextView) itemView.findViewById(
                        R.id.TVPID
                )).setText(
                        String.valueOf(
                                processInfo.pid
                        )
                );

                ((TextView) itemView.findViewById(
                        R.id.TVMemoryUsage
                )).setText(
                        processInfo.getFormattedMemoryUsage()
                );

                itemView.findViewById(
                        R.id.BTMenu
                ).setOnClickListener(
                        (v) -> showListItemMenu(
                                v,
                                processInfo
                        )
                );

                XServer xServer =
                        activity.getXServer();

                Window window;

                try (XLock xlock =
                             xServer.lock(
                                     XServer.Lockable.WINDOW_MANAGER
                             )) {

                    window =
                            xServer.windowManager
                                    .findWindowWithProcessId(
                                            processInfo.pid
                                    );
                }

                ImageView ivIcon =
                        itemView.findViewById(
                                R.id.IVIcon
                        );

                ivIcon.setImageResource(
                        R.drawable.taskmgr_process
                );

                if (window != null) {

                    Bitmap icon =
                            xServer.pixmapManager
                                    .getWindowIcon(window);

                    if (icon != null) {
                        ivIcon.setImageBitmap(icon);
                    }
                }

                if (index >= childCount) {
                    container.addView(itemView);
                }

                if (
                        index == numProcesses - 1 &&
                        childCount > numProcesses
                ) {

                    for (
                            int i = childCount - 1;
                            i >= numProcesses;
                            i--
                    ) {

                        container.removeViewAt(i);
                    }
                }
            }
        });
    }

    /*
     * ============================================================
     * CPU
     * ============================================================
     */

    private void updateCPUInfoView() {

        LinearLayout llCPUInfo =
                findViewById(R.id.LLCPUInfo);

        short[] clockSpeeds =
                CPUStatus.getCurrentClockSpeeds();

        if (
                clockSpeeds == null ||
                clockSpeeds.length == 0
        ) {
            return;
        }

        int totalClockSpeed = 0;
        short maxClockSpeed = 0;

        int childCount =
                llCPUInfo.getChildCount();

        for (
                int i = 0;
                i < clockSpeeds.length;
                i++
        ) {

            TextView textView;

            if (i < childCount) {

                textView =
                        (TextView)
                                llCPUInfo.getChildAt(i);

            } else {

                textView =
                        new TextView(activity);

                textView.setTextSize(
                        TypedValue.COMPLEX_UNIT_DIP,
                        11.5f
                );

                textView.setTextColor(
                        0xFFE0E0E0
                );

                textView.setSingleLine(true);

                textView.setPadding(
                        0,
                        1,
                        0,
                        1
                );

                llCPUInfo.addView(textView);
            }

            short clockSpeed =
                    CPUStatus.getMaxClockSpeed(i);

            textView.setText(
                    "CPU " +
                            i +
                            ": " +
                            clockSpeeds[i] +
                            "/" +
                            clockSpeed +
                            " MHz"
            );

            totalClockSpeed +=
                    clockSpeeds[i];

            maxClockSpeed =
                    (short) Math.max(
                            maxClockSpeed,
                            clockSpeed
                    );
        }

        while (
                llCPUInfo.getChildCount() >
                        clockSpeeds.length
        ) {

            llCPUInfo.removeViewAt(
                    llCPUInfo.getChildCount() - 1
            );
        }

        int avgClockSpeed =
                totalClockSpeed /
                        clockSpeeds.length;

        TextView tvCPUTitle =
                findViewById(
                        R.id.TVCPUTitle
                );

        byte cpuUsagePercent =
                (byte) (
                        (
                                (float) avgClockSpeed /
                                        maxClockSpeed
                        ) * 100.0f
                );

        tvCPUTitle.setText(
                "CPU (" +
                        cpuUsagePercent +
                        "%)"
        );
    }

    /*
     * ============================================================
     * MEMORY
     * ============================================================
     */

    private void updateMemoryInfoView() {

        ActivityManager activityManager =
                (ActivityManager)
                        activity.getSystemService(
                                Context.ACTIVITY_SERVICE
                        );

        ActivityManager.MemoryInfo memoryInfo =
                new ActivityManager.MemoryInfo();

        activityManager.getMemoryInfo(
                memoryInfo
        );

        /*
         * Memória física real do Android.
         */
        long realTotalMemory =
                memoryInfo.totalMem;

        long realUsedMemory =
                realTotalMemory -
                        memoryInfo.availMem;

        /*
         * Memória usada mostrada.
         *
         * No modo Auto:
         * usa a memória real.
         *
         * No modo manual:
         * usa o limite escolhido para a exibição.
         */
        long selectedMemory =
                getSelectedMemoryBytes();

        long displayTotalMemory;

        if (selectedMemory > 0) {
            displayTotalMemory =
                    selectedMemory;
        } else {
            displayTotalMemory =
                    realTotalMemory;
        }

        /*
         * Não deixa o valor usado ultrapassar
         * o valor total mostrado.
         */
        long displayUsedMemory =
                realUsedMemory;

        if (displayUsedMemory >
                displayTotalMemory) {

            displayUsedMemory =
                    displayTotalMemory;
        }

        byte memUsagePercent;

        if (displayTotalMemory > 0) {

            memUsagePercent =
                    (byte) (
                            (
                                    (double)
                                            displayUsedMemory /
                                            displayTotalMemory
                            ) * 100.0f
                    );

        } else {

            memUsagePercent = 0;
        }

        /*
         * Título:
         *
         * Memory (XX%)
         */
        TextView tvMemoryTitle =
                findViewById(
                        R.id.TVMemoryTitle
                );

        tvMemoryTitle.setText(
                activity.getString(
                        R.string.memory
                ) +
                        " (" +
                        memUsagePercent +
                        "%)"
        );

        /*
         * Informação:
         *
         * 3.2 GB / 4 GB ▼
         */
        TextView tvMemoryInfo =
                findViewById(
                        R.id.TVMemoryInfo
                );

        tvMemoryInfo.setSingleLine(true);

        String memoryText;

        if (selectedMemoryOption == 0) {

            memoryText =
                    StringUtils.formatBytes(
                            displayUsedMemory,
                            false
                    ) +
                            " / " +
                            StringUtils.formatBytes(
                                    displayTotalMemory,
                                    false
                            ) +
                            " ▼";

        } else {

            memoryText =
                    StringUtils.formatBytes(
                            displayUsedMemory,
                            false
                    ) +
                            " / " +
                            getSelectedMemoryText() +
                            " ▼";
        }

        tvMemoryInfo.setText(
                memoryText
        );

        /*
         * Tocar no valor da memória
         * abre o seletor.
         */
        tvMemoryInfo.setOnClickListener(
                this::showMemoryMenu
        );
    }
}
