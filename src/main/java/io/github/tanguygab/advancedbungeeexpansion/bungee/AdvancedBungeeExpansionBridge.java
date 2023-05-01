package io.github.tanguygab.advancedbungeeexpansion.bungee;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.github.tanguygab.advancedbungeeexpansion.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("UnstableApiUsage")
public class AdvancedBungeeExpansionBridge extends Plugin {

    protected final String CHANNEL = "advancedbungee:channel";
    private final List<String> loadedServers = new ArrayList<>();
    private final Map<String, ServerInfo> servers = new HashMap<>();
    private BungeeListener listener;

    @Override
    public void onEnable() {
        getProxy().getServers().forEach((server,info)->{
            ServerInfo serverInfo = new ServerInfo(server,playersGetNames(info.getPlayers()));
            info.ping((result, error) -> serverInfo.setStatus(error == null));
            servers.put(server,serverInfo);
        });
        getProxy().registerChannel(CHANNEL);
        getProxy().getServers().forEach((server,info)->loadServer(info));

        getProxy().getPluginManager().registerListener(this,listener = new BungeeListener(this));
        getProxy().getScheduler().schedule(this,()->
                getProxy().getServers().forEach((server, info)->info.ping((result, error) -> updateStatus(server,error == null))),
                0,10, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable() {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Unload");
        getProxy().getServers().forEach((server,info)-> info.sendData(CHANNEL, out.toByteArray()));
        getProxy().unregisterChannel(CHANNEL);
        getProxy().getPluginManager().unregisterListener(listener);
        getProxy().getScheduler().cancel(this);
        servers.clear();
        loadedServers.clear();
    }

    private List<String> playersGetNames(Collection<ProxiedPlayer> collection) {
        return collection.stream().map(ProxiedPlayer::getName).toList();
    }

    protected void loadServer(net.md_5.bungee.api.config.ServerInfo server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Load");
        out.writeUTF(server.getName());
        servers.forEach((target,info)->out.writeUTF(target+"|"+info.getStatus()+"|"+String.join(",",info.getPlayers())));
        out.writeUTF("End");
        boolean loaded = server.sendData(CHANNEL,out.toByteArray(),false);
        if (loaded) loadedServers.add(server.getName());
    }
    protected void updatePlayers(net.md_5.bungee.api.config.ServerInfo server) {
        if (server == null) return;

        if (server.getPlayers().size() != 0 && !loadedServers.contains(server.getName()))
            updateStatus(server.getName(),true);

        ServerInfo info = servers.get(server.getName());
        info.setPlayers(playersGetNames(server.getPlayers()));

        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Players");
        out.writeUTF(info.getName());
        out.writeUTF(String.join(",",info.getPlayers()));
        getProxy().getServers().forEach((target,info0)->info0.sendData(CHANNEL,out.toByteArray()));
    }
    private void updateStatus(String server, boolean status) {
        ServerInfo info = servers.get(server);

        if (status) loadServer(getProxy().getServerInfo(server));
        else loadedServers.remove(server);

        if (info.getStatus() == status) return;
        info.setStatus(status);


        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Status");
        out.writeUTF(server);
        out.writeBoolean(status);
        getProxy().getServers().forEach((target,info0)->info0.sendData(CHANNEL,out.toByteArray()));
    }

}