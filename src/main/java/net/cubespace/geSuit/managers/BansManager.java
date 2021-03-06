package net.cubespace.geSuit.managers;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import net.cubespace.Yamler.Config.InvalidConfigurationException;
import net.cubespace.geSuit.TimeParser;
import net.cubespace.geSuit.Utilities;
import net.cubespace.geSuit.geSuit;
import net.cubespace.geSuit.objects.Ban;
import net.cubespace.geSuit.objects.GSPlayer;
import net.cubespace.geSuit.objects.Track;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class BansManager {

    public static void banPlayer(String bannedBy, String player, String reason) {
    	banPlayer(bannedBy, player, reason, false);
    }

    public static void banPlayer(String bannedBy, String player, String reason, Boolean auto) {
        GSPlayer s = PlayerManager.getPlayer(bannedBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        if (t.gsp == null)
        	PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.UNKNOWN_PLAYER_STILL_BANNING);

        Ban b = DatabaseManager.bans.getBanInfo(t.name, t.uuid, null);
        if (b != null) {
        	if (b.getType().equals("tempban")) {
        		// We don't want tempbans AND bans in place.. it could cause issues!
        		DatabaseManager.bans.unbanPlayer(b.getId());
        	} else {
	            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_ALREADY_BANNED);
	            return;
        	}
        }

        if (reason == null || reason.equals("")) {
            reason = ConfigManager.messages.DEFAULT_BAN_REASON;
        }

        DatabaseManager.bans.banPlayer(t.name, t.uuid, null, bannedBy, reason, "ban");

        // Player is online so kick them
        if ((t.gsp != null) && (t.gsp.getProxiedPlayer() != null)) {
            disconnectPlayer(t.gsp.getProxiedPlayer(), Utilities.colorize(ConfigManager.messages.BAN_PLAYER_MESSAGE.replace("{message}", reason).replace("{sender}", bannedBy)));
        }

        if (ConfigManager.bans.BroadcastBans) {
            if (auto) {
            	PlayerManager.sendBroadcast(Utilities.colorize(ConfigManager.messages.BAN_PLAYER_AUTO_BROADCAST.replace("{player}", t.dispname).replace("{sender}", sender.getName())));
            } else {
            	PlayerManager.sendBroadcast(ConfigManager.messages.BAN_PLAYER_BROADCAST.replace("{player}", t.dispname).replace("{message}", reason).replace("{sender}", bannedBy));
            }
        } else {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.BAN_PLAYER_BROADCAST.replace("{player}", t.dispname).replace("{message}", reason).replace("{sender}", bannedBy));
        }

    }

    public static void unbanPlayer(String sentBy, String player) {
        GSPlayer s = PlayerManager.getPlayer(sentBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        if (!DatabaseManager.bans.isPlayerBanned(t.name, t.uuid, player)) {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_NOT_BANNED);
            return;
        }

        Ban b = DatabaseManager.bans.getBanInfo(t.name, t.uuid, player);

        DatabaseManager.bans.unbanPlayer(b.getId());

        if (ConfigManager.bans.BroadcastUnbans) {
            PlayerManager.sendBroadcast(ConfigManager.messages.PLAYER_UNBANNED.replace("{player}", t.dispname).replace("{sender}", sender.getName()));
        } else {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_UNBANNED.replace("{player}", t.dispname).replace("{sender}", sender.getName()));
        }
    }

    public static void banIP(String bannedBy, String player, String reason) {
        GSPlayer s = PlayerManager.getPlayer(bannedBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        if (reason.equals("")) {
            reason = Utilities.colorize(ConfigManager.messages.DEFAULT_BAN_REASON);
        }

        String ip;
        if (Utilities.isIPAddress(player)) {
            ip = player;
        } else {
            ip = DatabaseManager.players.getPlayerIP(player);
        }

        if (ip == null) {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_DOES_NOT_EXIST);
            return;
        }

        if (!DatabaseManager.bans.isPlayerBanned(ip)) {
            DatabaseManager.bans.banPlayer(player, null, ip, bannedBy, reason, "ipban");
        }

        for (GSPlayer p : PlayerManager.getPlayersByIP(ip)) {
            if (p.getProxiedPlayer() != null) {
                disconnectPlayer(p.getProxiedPlayer(), ConfigManager.messages.IPBAN_PLAYER.replace("{message}", reason).replace("{sender}", bannedBy));
            }
        }

        if (ConfigManager.bans.BroadcastBans) {
            PlayerManager.sendBroadcast(ConfigManager.messages.IPBAN_PLAYER_BROADCAST.replace("{player}", player).replace("{message}", reason).replace("{sender}", bannedBy));
        } else {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.IPBAN_PLAYER_BROADCAST.replace("{player}", player).replace("{message}", reason).replace("{sender}", bannedBy));
        }
    }

    public static void kickAll(String sender, String message) {
        if (message.equals("")) {
            message = ConfigManager.messages.DEFAULT_KICK_MESSAGE;
        }

        message = Utilities.colorize(ConfigManager.messages.KICK_PLAYER_MESSAGE.replace("{message}", message).replace("{sender}", sender));

        for (ProxiedPlayer p : ProxyServer.getInstance().getPlayers()) {
        	// Don't kick the player executing the command or anyone with bypass permission
        	if ((!p.hasPermission("gesuit.bypass.kickall")) && (!p.getName().equals(sender))) {
        		disconnectPlayer(p, message);
        	}
        }
    }

    public static void checkPlayersBan(String sentBy, String player) {
        GSPlayer s = PlayerManager.getPlayer(sentBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        Ban b = DatabaseManager.bans.getBanInfo(t.name);

        if (b == null) {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_NOT_BANNED);
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat();
            sdf.applyPattern("dd MMM yyyy HH:mm:ss z");
            PlayerManager.sendMessageToTarget(sender, ChatColor.DARK_AQUA + "-------- " + ChatColor.RED + "Ban Info" + ChatColor.DARK_AQUA + " --------");
            PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Player: " + ChatColor.AQUA + b.getPlayer());
            if (b.getUuid() != null) {
                PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "UUID: " + ChatColor.AQUA + b.getUuid());
            }
            PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Type: " + ChatColor.AQUA + b.getType());
            PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "By: " + ChatColor.AQUA + b.getBannedBy());
            PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Reason: " + ChatColor.AQUA + b.getReason());
            PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Date: " + ChatColor.AQUA + sdf.format(b.getBannedOn()));

            if (b.getBannedUntil() == null) {
                PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Until: " + ChatColor.AQUA + "-Forever-");
            } else {
                PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Until: " + ChatColor.AQUA + sdf.format(b.getBannedUntil()));
            }
        }
    }

    public static void displayPlayerBanHistory(String sentBy, String player) {
        GSPlayer s = PlayerManager.getPlayer(sentBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        List<Ban> bans = DatabaseManager.bans.getBanHistory(t.name);

        if (bans == null || bans.isEmpty()) {
            PlayerManager.sendMessageToTarget(sender, Utilities.colorize(ConfigManager.messages.PLAYER_NEVER_BANNED.replace("{player}", t.dispname)));
            return;
        }
        PlayerManager.sendMessageToTarget(sender, ChatColor.DARK_AQUA + "-------- " + ChatColor.YELLOW + player + "'s Ban History" + ChatColor.DARK_AQUA + " --------");
        boolean first = true;
        for (Ban b : bans) {
            if (first) {
                first = false;
            } else {
                PlayerManager.sendMessageToTarget(sender, "");
            }
            SimpleDateFormat sdf = new SimpleDateFormat();
            sdf.applyPattern("dd MMM yyyy HH:mm");

            PlayerManager.sendMessageToTarget(sender,
            		(b.getBannedUntil() != null ? ChatColor.GOLD : ChatColor.RED) + "Date: " +
            		ChatColor.GREEN + sdf.format(b.getBannedOn()) +
            		(b.getBannedUntil() != null ?
            				ChatColor.GOLD + " > " + sdf.format(b.getBannedUntil()) :
            				ChatColor.RED + " > permban"));

            PlayerManager.sendMessageToTarget(sender,
            		(b.getBannedUntil() != null ? ChatColor.GOLD : ChatColor.RED) + "By: " +
            		ChatColor.AQUA + b.getBannedBy() +
            		ChatColor.DARK_AQUA + " (" + ChatColor.GRAY + b.getReason() + ChatColor.DARK_AQUA + ")");
        }
    }

    public static void kickPlayer(String kickedBy, String player, String reason) {
    	kickPlayer(kickedBy, player, reason, false);
    }

    public static void kickPlayer(String kickedBy, String player, String reason, Boolean auto) {
        GSPlayer s = PlayerManager.getPlayer(kickedBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        if ((t.gsp == null) || (t.gsp.getProxiedPlayer() == null)) {
        	PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_NOT_ONLINE);
        	return;
        }

        if (reason.isEmpty()) {
            reason = ConfigManager.messages.DEFAULT_KICK_MESSAGE;
        }

        disconnectPlayer(t.gsp.getProxiedPlayer(), Utilities.colorize(ConfigManager.messages.KICK_PLAYER_MESSAGE.replace("{message}", reason).replace("{sender}", sender.getName())));
        	
        if (ConfigManager.bans.BroadcastKicks) {
            if (auto) {
            	PlayerManager.sendBroadcast(Utilities.colorize(ConfigManager.messages.KICK_PLAYER_AUTO_BROADCAST.replace("{player}", t.dispname).replace("{sender}", sender.getName())));
            } else {
            	PlayerManager.sendBroadcast(Utilities.colorize(ConfigManager.messages.KICK_PLAYER_BROADCAST.replace("{message}", reason).replace("{player}", t.dispname).replace("{sender}", sender.getName())));
            }
        }
    }

    public static void disconnectPlayer(ProxiedPlayer player, String message) {
        PlayerManager.unloadPlayer(player.getName());
        player.disconnect(new TextComponent(Utilities.colorize(message)));
    }

    public static void reloadBans(String sender) {
        PlayerManager.sendMessageToTarget(sender, "Bans Reloaded");

        try {
            ConfigManager.bans.reload();
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public static void tempBanPlayer(String bannedBy, String player, int seconds, String message) {
    	tempBanPlayer(bannedBy, player, seconds, message, false);
    }

    public static void tempBanPlayer(String bannedBy, String player, int seconds, String message, Boolean auto) {
        GSPlayer s = PlayerManager.getPlayer(bannedBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        if (t.gsp == null)
        	PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.UNKNOWN_PLAYER_STILL_BANNING);

        Ban b = DatabaseManager.bans.getBanInfo(t.name, t.uuid, null);
        if (b != null) {
        	if (b.getType().equals("tempban")) {
        		// We don't want tempbans AND bans in place.. it could cause issues!
        		DatabaseManager.bans.unbanPlayer(b.getId());
        	} else {
                PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.PLAYER_ALREADY_BANNED);
	            return;
        	}
        }

        if (message.equals("")) {
            message = ConfigManager.messages.DEFAULT_BAN_REASON;
        }

        Date sqlToday = new Date(System.currentTimeMillis() + (seconds * 1000));
        SimpleDateFormat sdf = new SimpleDateFormat();
        sdf.applyPattern("dd MMM yyyy HH:mm:ss");
        String time = sdf.format(sqlToday);
        sdf.applyPattern("yyyy-MM-dd HH:mm:ss");
        String timeDiff = Utilities.buildTimeDiffString(seconds * 1000, 2);
        String shortTimeDiff = Utilities.buildShortTimeDiffString(seconds * 1000, 10);

        DatabaseManager.bans.tempBanPlayer(t.name, t.uuid, bannedBy, message, sdf.format(sqlToday));

        if ((t.gsp != null) && (t.gsp.getProxiedPlayer() != null)) {
            disconnectPlayer(t.gsp.getProxiedPlayer(), ConfigManager.messages.TEMP_BAN_MESSAGE.replace("{sender}", t.dispname).replace("{time}", time).replace("{left}", timeDiff).replace("{shortleft}", shortTimeDiff).replace("{message}", message));
        }

        if (ConfigManager.bans.BroadcastBans) {
            if (auto) {
            	PlayerManager.sendBroadcast(Utilities.colorize(ConfigManager.messages.TEMP_BAN_AUTO_BROADCAST.replace("{player}", t.dispname).replace("{sender}", sender.getName()).replace("{time}", time).replace("{left}", timeDiff).replace("{shortleft}", shortTimeDiff)));
            } else {
            	PlayerManager.sendBroadcast(ConfigManager.messages.TEMP_BAN_BROADCAST.replace("{player}", t.dispname).replace("{sender}", sender.getName()).replace("{message}", message).replace("{time}", time).replace("{left}", timeDiff).replace("{shortleft}", shortTimeDiff));
            }
        } else {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.TEMP_BAN_BROADCAST.replace("{player}", t.dispname).replace("{sender}", sender.getName()).replace("{message}", message).replace("{time}", time).replace("{left}", timeDiff).replace("{shortleft}", shortTimeDiff));
        }
    }

    public static boolean checkTempBan(Ban b) {
        java.util.Date today = new java.util.Date(Calendar.getInstance().getTimeInMillis());
        java.util.Date banned = b.getBannedUntil();

        if (today.compareTo(banned) >= 0) {
            DatabaseManager.bans.unbanPlayer(b.getId());
            return false;
        }

        return true;
    }

    public static void warnPlayer(String warnedBy, String player, String reason) {
        GSPlayer s = PlayerManager.getPlayer(warnedBy);
        CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        BanTarget t = getBanTarget(player);
        if (t.gsp == null) {
        	PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.UNKNOWN_PLAYER_NOT_WARNING);
        	return;
    	}
        
        if (reason == null || reason.isEmpty()) {
            reason = ConfigManager.messages.DEFAULT_WARN_REASON;
        }

        DatabaseManager.bans.warnPlayer(t.name, t.uuid, warnedBy, reason);

        if (ConfigManager.bans.BroadcastWarns) {
            PlayerManager.sendBroadcast(ConfigManager.messages.WARN_PLAYER_BROADCAST.replace("{player}", t.dispname).replace("{message}", reason).replace("{sender}", warnedBy));
        } else {
            PlayerManager.sendMessageToTarget(sender, ConfigManager.messages.WARN_PLAYER_BROADCAST.replace("{player}", t.dispname).replace("{message}", reason).replace("{sender}", warnedBy));
        }

        // Check if we have warning actions defined
        if (ConfigManager.bans.Actions != null) {
        	List<Ban> warnings = DatabaseManager.bans.getWarnHistory(t.name, t.uuid);
        	Integer warncount = warnings.size();
        	if (ConfigManager.bans.Actions.containsKey(warncount)) {
        		String fullaction = ConfigManager.bans.Actions.get(warncount);
        		String[] parts = fullaction.split(" ");

        		String action = parts[0];
        		if (action.equals("kick")) {
        			if ((t.gsp != null) && (t.gsp.getProxiedPlayer() != null)) {
        				kickPlayer(warnedBy, t.name, reason, true);
        			}
        		}
        		else if (action.equals("tempban")) {
        	        int seconds = TimeParser.parseString(parts[1]);
        			tempBanPlayer(warnedBy, t.name, seconds, reason, true);
        		}
        		else if (action.equals("ban")) {
        			banPlayer(warnedBy, t.name, reason, true);
        		} else {
                	PlayerManager.sendMessageToTarget(sender, ChatColor.RED + "Warning action of \"" + fullaction + "\" is invalid!");
        			LoggingManager.log(ChatColor.RED + "Warning action of \"" + fullaction + "\" is invalid!");
        		}
        	}
        }
    }

    public static void displayPlayerWarnHistory(String sender, String player) {
        GSPlayer p = PlayerManager.getPlayer(sender);
        List<Ban> warns = DatabaseManager.bans.getWarnHistory(player, player);

        if (warns == null || warns.isEmpty()) {
            PlayerManager.sendMessageToTarget(p, Utilities.colorize(ConfigManager.messages.PLAYER_NEVER_WARNED.replace("{player}", player)));
            return;
        }
        PlayerManager.sendMessageToTarget(p, ChatColor.DARK_AQUA + "-------- " + ChatColor.YELLOW + player + "'s Warning History" + ChatColor.DARK_AQUA + " --------");
        
        int count = 0;
        for (Ban b : warns) {
        	count++;
            SimpleDateFormat sdf = new SimpleDateFormat();
            sdf.applyPattern("dd MMM yyyy HH:mm");
            PlayerManager.sendMessageToTarget(p,
            		ChatColor.YELLOW + String.valueOf(count) + ": " +
            		ChatColor.GREEN + sdf.format(b.getBannedOn()) +
            		ChatColor.YELLOW + " (" + ChatColor.GRAY + b.getBannedBy() + ChatColor.YELLOW + ") " +
            		ChatColor.AQUA + b.getReason());            		
        }
    }

    public static void displayWhereHistory(String sentBy, String options, String search) {
        GSPlayer s = PlayerManager.getPlayer(sentBy);
        final CommandSender sender = (s == null ? ProxyServer.getInstance().getConsole() : s.getProxiedPlayer());

        List<Track> tracking = null;
    	if (search.contains(".")) {
    		tracking = DatabaseManager.tracking.getPlayerTracking(search, "ip");
    		if (tracking.isEmpty()) { 
                PlayerManager.sendMessageToTarget(sender,
                		ChatColor.RED + "[Tracker] No known accounts match or contain \"" + search + "\"");
                return;
    		} else {
    			PlayerManager.sendMessageToTarget(sender,
            		ChatColor.GREEN + "[Tracker] IP address \"" + search + "\" associated with " + tracking.size() + " accounts:");
    		}
    	} else {
    		String type;
    		if (search.length() > 20) {
    			type = "uuid";
    			search = search.replace("-", "");
    		} else {
    			type = "name";
    		}

    		if (!DatabaseManager.players.playerExists(search)) {
    			// No exact match... do a partial match
                PlayerManager.sendMessageToTarget(sender,
                		ChatColor.AQUA + "[Tracker] No accounts matched exactly \"" + search + "\", trying wildcard search..");

                List<String> matches = DatabaseManager.players.matchPlayers(search);
	    		if (matches.isEmpty()) { 
	                PlayerManager.sendMessageToTarget(sender,
	                		ChatColor.RED + "[Tracker] No known accounts match or contain \"" + search + "\"");
	                return;
	    		}
	    		else if (matches.size() == 1) {
		    		if (search.length() < 20) {
		    			search = matches.get(0);
		    		}
	    		} else {
	    			// Matched too many names, show list of names instead
	                PlayerManager.sendMessageToTarget(sender,
	                		ChatColor.RED + "[Tracker] More than one player matched \"" + search + "\":");
	    	    	for (String m : matches) {
	    	            PlayerManager.sendMessageToTarget(sender,
	    	            		ChatColor.AQUA + " - " + m);
	    	        }
	                return;
	    		}
    		}
    		
    		tracking = DatabaseManager.tracking.getPlayerTracking(search, type);
    		if (tracking.isEmpty()) { 
                PlayerManager.sendMessageToTarget(sender,
                		ChatColor.GREEN + "[Tracker] No known accounts match or contain \"" + search + "\"");
                return;
    		} else {
    			PlayerManager.sendMessageToTarget(sender,
            		ChatColor.GREEN + "[Tracker] Player \"" + search + "\" associated with " + tracking.size() + " accounts:");
    			
    			if (geSuit.proxy.getPlayer(search) != null) {
    			    final ProxiedPlayer player = geSuit.proxy.getPlayer(search);
    			    geSuit.proxy.getScheduler().runAsync(geSuit.instance, new Runnable() {
    		            @Override
    		            public void run() {
    		                String location = GeoIPManager.lookup(player.getAddress().getAddress());
    		                if (location != null) {
    		                    PlayerManager.sendMessageToTarget(sender, ChatColor.GREEN + "[Tracker] Player " + player.getName() + "'s IP resolves to " + location); 
    		                }
    		            }
    		        });
    			}
    		}
    	}

    	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    	for (Track t : tracking) {
            PlayerManager.sendMessageToTarget(sender,
            		ChatColor.DARK_GREEN + " - " + t.getPlayer() + " " + ChatColor.DARK_AQUA + t.getIp() + ChatColor.GRAY + " (" + sdf.format(t.getLastSeen()) + ")");
        }
    }

    private static class BanTarget {
    	String name = null;
    	String dispname = null;
		String uuid = null;
    	GSPlayer gsp = null;
    }
    
    private static BanTarget getBanTarget(String player) {
    	BanTarget target = new BanTarget();

    	// Try to find the player online
    	GSPlayer t = PlayerManager.matchOnlinePlayer(player);
        
        // If they are not online, try to find them as an offline player
        if (t == null) {
        	t = DatabaseManager.players.loadPlayer(player);
        }

        // Set up the target + display name we should use
        if (t == null) {
        	// Can't find this player so just use whatever player string was given to us
    		target.name = player;
    		target.dispname = player;
        } else {
        	// Get their real name, UUID and display name (alias)
        	if ((t.getUuid() != null) && (!t.getUuid().isEmpty())) {
        		target.uuid = t.getUuid();
        	}
        	target.name = t.getName();

        	// Get their display name (used for broadcasts and messages)
        	if (t.getProxiedPlayer() != null) {
        		target.dispname = t.getProxiedPlayer().getDisplayName();
        	} else {
        		target.dispname = t.getName();
        	}
        	target.gsp = t;
        }
    	return target;
    }
}
