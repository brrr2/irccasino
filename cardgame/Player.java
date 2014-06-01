/*
    Copyright (C) 2013-2014 Yizhe Shen <brrr@live.ca>

    This file is part of irccasino.

    irccasino is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    irccasino is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with irccasino.  If not, see <http://www.gnu.org/licenses/>.
 */

package irccasino.cardgame;

import irccasino.Stats;
import java.util.Comparator;
import org.pircbotx.Colors;

/**
 * A player class with common methods and members for all types of players.
 * It serves as a template and should not be directly instantiated.
 * @author Yizhe Shen
 */
abstract public class Player extends Stats {
    /** Stores the player's nick. */
    private String nick;
    /** Stores the player's host. */
    private String host;

    /**
     * Creates a new Player.
     * Not to be instantiated directly. Serves as the template for specific types
     * of players.
     * 
     * @param nick IRC user nick
     * @param host IRC user host
     */
    public Player(String nick, String host){
        super();
        this.nick = nick;
        this.host = host;
        set("id", 0);
        set("cash", 0);
        set("bank", 0);
        set("bankrupts", 0);
        set("bjrounds", 0);
        set("bjwinnings", 0);
        set("tprounds", 0);
        set("tpwinnings", 0);
        set("ttwins", 0);
        set("ttplayed", 0);
        set("quit", 0);
    }

    /* Player info methods */
    /**
     * Returns the Player's nick.
     * 
     * @return the Player's nick
     */
    public String getNick(){
        return getNick(true);
    }

    /**
     * Allows a nick to be returned with a zero-space character.
     * @param ping whether or not to add a zero-width character in nick
     * @return the Player's nick
     */
    public String getNick(boolean ping) {
        if (ping) {
            return nick;
        } else {
            return nick.substring(0, 1) + "\u200b" + nick.substring(1);
        }
    }

    /**
     * Returns the player's nick formatted in IRC bold.
     * 
     * @return the bold-formatted nick
     */
    public String getNickStr(){
        return getNickStr(true);
    }

    /**
     * Allows a bolded nick to be returned with a zero-space character.
     * 
     * @param ping whether or not to add a zero-width character in nick
     * @return the bold-formatted nick
     */
    public String getNickStr(boolean ping){
        return Colors.BOLD + getNick(ping) + Colors.BOLD;
    }

    /**
     * Returns the Player's host.
     * 
     * @return the Player's host
     */
    public String getHost() {
        return host;
    }

    @Override
    public int get(String stat){
        if (stat.equalsIgnoreCase("exists")){
            return 1;
        } else if (stat.equalsIgnoreCase("netcash")){
            return get("cash") + get("bank");
        }
        return super.get(stat);
    }

    /**
     * Transfers the specified amount from cash into bank.
     * 
     * @param amount the amount to transfer
     */
    public void bankTransfer(int amount){
        add("bank", amount);
        add("cash", -1 * amount);
    }

    /**
     * String representation includes the Player's nick and host.
     * 
     * @return a String containing the Players nick and host
     */
    @Override
    public String toString(){
        return nick + " " + host;
    }

    /**
     * Comparison of Player objects based on nick and host.
     * @param o the Object to compare
     * @return true if the properties are the same
     */
    @Override
    public boolean equals(Object o) {
        if (o != null && o instanceof Player) {
            Player p = (Player) o;
            if (nick.equals(p.nick) && host.equals(p.host) &&
                hashCode() == p.hashCode()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Auto-generated hashCode method.
     * @return the Player's hashCode
     */
    @Override
    public int hashCode() {
        int hash = 5;
        hash = 29 * hash + nick.hashCode();
        hash = 29 * hash + host.hashCode();
        return hash;
    }
    
    /**
     * Returns a comparator that compares the given stat.
     * @param stat the stat to use for comparison
     * @return a new comparator
     */
    public static Comparator<Player> getComparator(final String stat) {
        return new Comparator<Player>() {
            @Override
            public int compare(Player a, Player b) {
                if (a.get(stat) < b.get(stat)) {
                    return 1;
                } else if (a.get(stat) > b.get(stat)) {
                    return -1;
                }
                return 0;
            }
        };
    }
}