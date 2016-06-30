#include <gtk/gtk.h>
#include <stdio.h>
#include <stdlib.h>

typedef struct
{
    GtkWidget  *progressBar;
    gint32     totalProgress;
} CallbackData;

static gboolean
stdin_callback(GIOChannel * io, GIOCondition condition, gpointer data);

int main (int argc, char *argv[])
{
    GtkWidget *window;
    GtkWidget *label;
    GtkWidget *progressBar;
    GtkWidget *progressBox;
    GtkWidget *iconBox;
    GtkWidget *comboBox;
    GtkWidget *icon;
    GdkPixbuf *pixbuf;
    GError    *error;
    GIOChannel *io;
    gint32 totalProgress = 0;

    // Verify that total progress is a positive int value
    gtk_init(&argc, &argv);
    if (argc == 2) {
        totalProgress = atoi(argv[1]);
    }
    if (totalProgress < 1) {
        return 0;
    }

    // Create the main, top level window
    window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_window_set_title(GTK_WINDOW(window), "AeroFS");

    // Set the window size and position
    gtk_window_set_default_size(GTK_WINDOW(window), 300, 110);
    gtk_container_set_border_width (GTK_CONTAINER (window), 20);
    gtk_window_set_position(GTK_WINDOW(window), GTK_WIN_POS_CENTER);

    // Try to prevent the window from showing up in the taskbar
    gtk_window_set_skip_taskbar_hint(GTK_WINDOW(window), TRUE);

    // Try to prevent the window from offering a close button
    gtk_window_set_type_hint(GTK_WINDOW(window), GDK_WINDOW_TYPE_HINT_UTILITY);
    gtk_window_set_deletable(GTK_WINDOW(window), FALSE);

    // Create a label, progress bar
    char *labelText = "AeroFS is upgrading to the latest version.\nThis may take a few minutes.";
    label = gtk_label_new(labelText);
    gtk_label_set_justify(GTK_LABEL(label), GTK_JUSTIFY_CENTER);
    progressBar = gtk_progress_bar_new();

    // Load an icon from the compiled-in resource
    error = NULL;
    pixbuf = gdk_pixbuf_new_from_resource_at_scale("/com/aerofs/progress-monitor/logo.png", 80, 80, TRUE, &error);
    if (pixbuf) {
        icon = gtk_image_new_from_pixbuf(pixbuf);
    } else {
        if (error) {
            g_error_free(error);
        }
        return 0;
    }

    // Create a box for progress indication
    progressBox = gtk_box_new(GTK_ORIENTATION_VERTICAL, 0);
    gtk_box_pack_start(GTK_BOX(progressBox), label, TRUE, FALSE, 0);
    gtk_box_pack_end(GTK_BOX(progressBox), progressBar, TRUE, FALSE, 0);

    // Create an icon box
    iconBox = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 0);
    gtk_box_pack_start(GTK_BOX(iconBox), icon, TRUE, TRUE, 0);

    // Add the boxes to the main window
    comboBox = gtk_box_new(GTK_ORIENTATION_HORIZONTAL, 10);
    gtk_box_pack_start(GTK_BOX(comboBox), iconBox, TRUE, FALSE, 0);
    gtk_box_pack_end(GTK_BOX(comboBox), progressBox, TRUE, FALSE, 0);
    gtk_container_add(GTK_CONTAINER(window), comboBox);

    // Prevent further resizing and show the window and all its children
    gtk_window_set_resizable(GTK_WINDOW(window), FALSE);
    gtk_widget_show_all(window);

    // Handle listening on stdin
    CallbackData data = { progressBar, totalProgress };
    io = g_io_channel_unix_new(STDIN_FILENO);
    g_io_add_watch(io, G_IO_IN, stdin_callback, &data);
    g_io_channel_unref(io);

    // Start the application's main loop
    gtk_main();

    return 0;
}

static gboolean
stdin_callback(GIOChannel * io, GIOCondition condition, gpointer data)
{
    gchar *line;
    gint32 downloadProgress;
    gdouble progressFraction;
    CallbackData *callbackData = (CallbackData *) data;
    gint32 totalProgress = callbackData->totalProgress;

    switch(g_io_channel_read_line(io, &line, NULL, NULL, NULL)) {
        case G_IO_STATUS_NORMAL:
            // read progress from stdin
            // if it is nonsense or indicates completion, quit
            downloadProgress = atoi(line);
            if (downloadProgress < 1 || downloadProgress >= totalProgress) {
                g_free(line);
                gtk_main_quit();
                return FALSE;
            }
            // update the progress bar
            progressFraction = ((double)downloadProgress)/((double)totalProgress);
            gtk_progress_bar_set_fraction(GTK_PROGRESS_BAR(callbackData->progressBar), progressFraction);
            g_free(line);
            return TRUE;
        case G_IO_STATUS_ERROR:
            gtk_main_quit();
            return FALSE;
        case G_IO_STATUS_EOF:
            return FALSE;
        case G_IO_STATUS_AGAIN:
            return TRUE;
        default:
            gtk_main_quit();
            return FALSE;
            break;
    }
    return FALSE;
}
