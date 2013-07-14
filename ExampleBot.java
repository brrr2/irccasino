/*
	Copyright (C) 2013 Yizhe Shen <brrr@live.ca>
	
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

import javax.net.ssl.SSLSocketFactory;

import org.pircbotx.*;
import org.pircbotx.cap.SASLCapHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;
import org.pircbotx.hooks.managers.*;

public class ExampleBot extends ListenerAdapter<PircBotX> {
	
	public static PircBotX bot;
	public static ThreadedListenerManager<PircBotX> manager;
	public static CardGame game;
	public static String nick, password, network, channel;
	public static final char commandChar = '.';
	    
	public static void main(String[] args) throws Exception {
		/* Check number of arguments */
        if (args.length == 4){
        	nick = args[0];
        	password = args[1];
        	network = args[2];
        	channel = "##"+args[3];
        } else if (args.length == 2){
        	network = args[0];
        	channel = "##"+args[1];
        } else {
        	System.out.println("2 or 4 parameters required. Unable to continue. " +
        			"[nick] [password] network channel.");
        	System.exit(0);
        }
        
	    //Create Listener Manager to use
	    manager = new ThreadedListenerManager<PircBotX>();
	    manager.addListener(new ExampleBot());
	    
	    bot = new PircBotX();
	    bot.setListenerManager(manager);
	    bot.setVerbose(true);
	    bot.setAutoNickChange(true);
	    bot.setCapEnabled(true);

	    if (args.length == 4){
        	nick = args[0];
        	password = args[1];
        	network = args[2];
        	channel = "##"+args[3];
        	bot.setName(nick);
    	    bot.setLogin(nick);
        	try {
    	    	bot.getCapHandlers().add(new SASLCapHandler(nick, password));
            	bot.connect(network,7000,SSLSocketFactory.getDefault());
            } catch (Exception e){
            	System.out.println("Error connecting to "+network);
            }
        } else if (args.length == 2){
        	network = args[0];
        	channel = "##"+args[1];
        	bot.setName("ExampleBot");
    	    bot.setLogin("ExampleBot");
    	    try {
            	bot.connect(network);
            } catch (Exception e){
            	System.out.println("Error connecting to "+network);
            }
        }

	}
	
	@Override
    public void onMessage (MessageEvent<PircBotX> event){
        String msg = event.getMessage().toLowerCase();
        Channel channel = event.getChannel();
        User user = event.getUser();
        if (msg.length() > 1 && msg.charAt(0) == commandChar && channel.isOp(user)){
        	msg = msg.substring(1);
            if (msg.equals("blackjack") || msg.equals("bj")) {
                if (game == null){
                	game = new Blackjack(event.getBot(), channel, commandChar);
                	game.showGameStart();
                    manager.addListener(game);
                } else {
                    bot.sendMessage(channel,"A game is already running!");
                }
            } else if (msg.equals("texaspoker") || msg.equals("tp")) {
            	if (game == null){
                	game = new TexasPoker(event.getBot(), channel, commandChar);
                	game.showGameStart();
                    manager.addListener(game);
                } else {
                    bot.sendMessage(channel,"A game is already running!");
                }
            } else if (msg.equals("endgame")) {
                if (game == null){
                    bot.sendMessage(channel, "No game is currently running!");
                } else {
                    game.showGameEnd();
                    game.endGame();
                    manager.removeListener(game);
                    game = null;
                }
            } else if (msg.equals("shutdown") || msg.equals("botquit")) {
                if (game != null){
                	game.showGameEnd();
                	game.endGame();
                    manager.removeListener(game);
                    game = null;
                }
                try {
                	bot.quitServer();
                } catch (Exception e){
                	System.out.println("Exception caught during disconnection.");
                }
            }
        }
    }
	
	@Override
    public void onConnect (ConnectEvent<PircBotX> event){
		bot.joinChannel(channel);
    }

	/*
	@Override
    public void onJoin (JoinEvent<PircBotX> event){
		Channel channel = event.getChannel();
		User user = event.getUser();
		
		if (user.getNick().toLowerCase().equals(bot.getNick().toLowerCase())){
			if (game == null){
	        	game = new Blackjack(event.getBot(), channel, commandChar);
	        	game.showGameStart();
	            manager.addListener(game);
	        } else {
	            bot.sendMessage(channel,"A game is already running!");
	        }
		}
    }
    */
}
