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
package irccasino;

import irccasino.cardgame.CardGame;
import java.util.Set;
import org.pircbotx.Channel;
import org.pircbotx.User;

/**
 * An interface for managing games that can be implemented by any PircBotX bot.
 * @author Yizhe Shen
 */
public interface GameManager {
    
    /**
     * Checks if a game is running in the specified channel.
     * @param channel the channel to check
     * @return true if the channel has a game already running
     */
    public boolean hasGame(Channel channel);
        
    /**
     * Returns the CardGame that's running in the specified channel.
     * @param channel the channel to check
     * @return the CardGame or null if no game is running
     */
    public CardGame getGame(Channel channel);
    
    /**
     * Returns the CardGame that the specified nick has joined or is on the 
     * waitlist.
     * @param nick the player's nick
     * @return the CardGame or null if not joined or waitlisted
     */
    public CardGame getGame(String nick);
    
    /**
     * Checks if a player is bankrupt.
     * @param nick the player's nick
     * @return true if the player is bankrupt
     */
    public boolean isBlacklisted(String nick);
        
    /**
     * Checks if any games have rounds in progress.
     * @return true if any game has a round in progress
     */
    public boolean checkGamesInProgress();
    
    /**
     * Starts a new game.
     * @param game the new game
     */
    public void startGame(CardGame game);
    
    /**
     * Shuts down the specified game.
     * @param game the game to shut down
     */
    public void endGame(CardGame game);
    
    /**
     * Shuts down all games.
     */
    public void endAllGames();
    
    /**
     * Sends a message to a channel. Automatically implemented by any PircBotX
     * which implements this interface.
     * @param channel the Channel to send message
     * @param msg the message
     */
    public void sendMessage(Channel channel, String msg);
        
    /**
     * Sends a message to a target. Automatically implemented by any PircBotX
     * which implements this interface.
     * @param target the target as a String
     * @param msg the message
     */
    public void sendMessage(String target, String msg);
    
    /**
     * Sends a notice to a target. Automatically implemented by any PircBotX
     * which implements this interface.
     * @param target the target as a String
     * @param msg the message
     */
    public void sendNotice(String target, String msg);
    
    /**
     * Voices a user in a channel. Automatically implemented by any PircBotX
     * which implements this interface.
     * @param channel
     * @param user 
     */
    public void voice(Channel channel, User user);
    
    /**
     * Devoices a user in a channel. Automatically implemented by any PircBotX
     * which implements this interface.
     * @param channel the Channel containing the user
     * @param user the User to deVoice
     */
    public void deVoice(Channel channel, User user);
    
    /**
     * Makes a log entry. Automatically implemented by any PircBotX which 
     * implements this interface.
     * @param line 
     */
    public void log(String line);
    
    /**
     * Retrieves the Users in a Channel as a Set. Automatically implemented by
     * any PircBotX which implements this interface.
     * @param channel
     * @return a Set of Users
     */
    public Set<User> getUsers(Channel channel);
    
    /**
     * Sends a raw line to the IRC server. Automatically implemented by any
     * PircBotX which implements this interface.
     * @param line 
     */
    public void sendRawLine(String line);
}