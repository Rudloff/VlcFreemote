package com.nico.vlcfreemote.vlc_connector;

import android.util.Base64;

import com.nico.vlcfreemote.net_utils.Server;
import com.nico.vlcfreemote.vlc_connector.http_utils.Wget;

import java.util.Comparator;
import java.util.PriorityQueue;


public class RemoteVlc implements VlcStatus.ObserverRegister,
                                  Wget.CallbackWhenTaskFinished {

    /**
     * An interface to retrieve the current active connection: useful so that fragments won't need
     * to keep a field across constructions/displays
     */
    public interface ConnectionProvider {
        RemoteVlc getActiveVlcConnection();
    }

    private final String base_url;
    private final String hashedPass;
    private final VlcCommand.GeneralCallback general_cb;
    private final VlcStatus.Observer statusObserver;
    private final Server srv;
    private Wget lastCmd = null;
    private  VlcCommand lowPriorityCommand = null;
    private PriorityQueue<VlcCommand> pendingCommands;

    /**
     * Construct an object to connect to a remote Vlc
     * @param srv Server to connect to
     * @param cbs A general callback object that should implement both VlcCommand.GeneralCallback
     *            and VlcStatus.ObserverRegister.
     */
    public <Callbacks extends VlcCommand.GeneralCallback & VlcStatus.Observer>
        RemoteVlc(final Server srv, final Callbacks cbs) {
        base_url = "http://" + srv.ip + ":" + srv.vlcPort + "/";
        hashedPass = "Basic " + Base64.encodeToString((":" + srv.getPassword()).getBytes(), Base64.DEFAULT);
        this.general_cb = cbs;
        this.statusObserver = cbs;
        this.srv = srv;

        this.pendingCommands = new PriorityQueue<>(10, new Comparator<VlcCommand>() {
            @Override
            public int compare(VlcCommand cmd1, VlcCommand cmd2) {
                return cmd1.getPriority().getValue() - cmd2.getPriority().getValue();
            }
        });
    }

    public synchronized void exec(final VlcCommand cmd) {
        if (lastCmd != null) {
            queueCommand(cmd);
            return;
        }

        this.lastCmd = new Wget(this.getBaseUrl() + cmd.getCommandPath(), this.getAuth(),
                        cmd.getWgetCallback(general_cb), this);
    }

    private void queueCommand(final VlcCommand cmd) {
        if (cmd.getPriority() == VlcCommand.Priority.CanIgnore) {
            // Only keep a single "ignore" command
            lowPriorityCommand = cmd;
        } else {
            pendingCommands.add(cmd);
        }
    }

    @Override
    public synchronized void onTaskFinished() {
        lastCmd = null;

        if (! pendingCommands.isEmpty()) {
            VlcCommand cmd = pendingCommands.remove();
            exec(cmd);
        } else if (lowPriorityCommand != null) {
            exec(lowPriorityCommand);
            lowPriorityCommand = null;
        }
    }

    @Override
    public VlcStatus.Observer getVlcStatusObserver() {
        return statusObserver;
    }

    private String getBaseUrl() { return base_url; }
    private String getAuth() { return hashedPass; }

    public Server getServer() { return srv; }
}