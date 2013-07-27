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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import org.pircbotx.*;

public class TexasPoker extends CardGame{
    /* A pot class to handle bets and payouts in Texas Hold'em Poker. */
	public class PokerPot {
		private ArrayList<PokerPlayer> players;
		private int pot;
		
		public PokerPot(){
			pot = 0;
			players = new ArrayList<PokerPlayer>();
		}
		
		public int getPot(){
			return pot;
		}
		public void addPot(int amount){
			pot += amount;
		}
		public void addPlayer(PokerPlayer p){
			players.add(p);
		}
		public void removePlayer(PokerPlayer p){
			players.remove(p);
		}
		public PokerPlayer getPlayer(int c){
			return players.get(c);
		}
		public ArrayList<PokerPlayer> getPlayers(){
			return players;
		}
		public boolean hasPlayer(PokerPlayer p){
			return players.contains(p);
		}
		public int getNumberPlayers(){
			return players.size();
		}
	}
	
	private int stage, currentBet, minRaise, minBet;
	private ArrayList<PokerPot> pots;
	private PokerPot currentPot;
	private PokerPlayer dealer, smallBlind, bigBlind, topBettor;
	private Hand community;
	
	/**
	 * Constructor for TexasPoker, subclass of CardGame
     * 
	 * @param parent The bot that created an instance of this ListenerAdapter
	 * @param gameChannel The IRC channel in which the game is to be run.
     * @param eb The ListenerAdapter that is listening for commands for this game
	 */
	public TexasPoker(PircBotX parent, Channel gameChannel, ExampleBot eb){
		super(parent, gameChannel, eb);
		gameName = "Texas Hold'em Poker";
		deck = new CardDeck();
		deck.shuffleCards();
		loadSettings();
		pots = new ArrayList<PokerPot>();
		community = new Hand();
		stage = 0;
		currentBet = 0;
		currentPot = null;
		dealer = null;
		smallBlind = null;
		bigBlind = null;
		topBettor = null;
        maxPlayers = 22;
	}
	
    /* Command management method */
    @Override
    public void processCommand(User user, String command, String[] params){
        String nick = user.getNick();
        String hostmask = user.getHostmask();

        /* Parsing commands from the channel */
        if (command.equals("join") || command.equals("j")) {
            if (parentListener.bjgame != null &&
                (parentListener.bjgame.isJoined(nick) || parentListener.bjgame.isWaitlisted(nick))){
                bot.sendNotice(user, "You're already joined in "+parentListener.bjgame.getGameNameStr()+"!");
            } else if (parentListener.bjgame != null && parentListener.bjgame.isBlacklisted(nick)){
                infoBlacklisted(nick);
            } else{
                join(nick, hostmask);
            }
        } else if (command.equals("leave") || command.equals("quit") || command.equals("l") || command.equals("q")) {
            leave(nick);
        } else if (command.equals("start") || command.equals("go")) {
            if (isStartCommandAllowed(nick)){
                setInProgress(true);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("bet") || command.equals("b")) {
            if (isPlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("check") || command.equals("c") || command.equals("call")) {
            if (isPlayerTurn(nick)){
                checkCall();
            }
        } else if (command.equals("fold") || command.equals("f")) {
            if (isPlayerTurn(nick)){
                fold();
            }
        } else if (command.equals("raise") || command.equals("r")) {
            if (isPlayerTurn(nick)){
                if (params.length > 0){
                    try {
                        raise(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("allin") || command.equals("a")){
            if (isPlayerTurn(nick)){
                bet(Integer.MAX_VALUE);
            }
        } else if (command.equals("community") || command.equals("comm")){
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (!isInProgress()) {
                infoNotStarted(nick);
            } else if (stage == 0){
                infoNoCommunity(nick);
            } else {
                showCommunityCards();
            }
        } else if (command.equals("hand")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (!isInProgress()) {
                infoNotStarted(nick);
            } else {
                PokerPlayer p = (PokerPlayer) findJoined(nick);
                infoPlayerHand(p, p.getHand());
            }
        } else if (command.equals("turn")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (!isInProgress()) {
                infoNotStarted(nick);
            } else {
                showTurn(currentPlayer);
            }
        } else if (command.equals("simple")) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else {
                togglePlayerSimple(nick);
            }
        } else if (command.equals("stats")){
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                showGameStats();
            }
        } else if (command.equals("cash")) {
            if (params.length > 0){
                showPlayerCash(params[0]);
            } else {
                showPlayerCash(nick);
            }
        } else if (command.equals("netcash") || command.equals("net")) {
            if (params.length > 0){
                showPlayerNetCash(params[0]);
            } else {
                showPlayerNetCash(nick);
            }
        } else if (command.equals("debt")) {
            if (params.length > 0){
                showPlayerDebt(params[0]);
            } else {
                showPlayerDebt(nick);
            }
        } else if (command.equals("bankrupts")) {
            if (params.length > 0){
                showPlayerBankrupts(params[0]);
            } else {
                showPlayerBankrupts(nick);
            }
        } else if (command.equals("rounds")) {
            if (params.length > 0){
                showPlayerRounds(params[0]);
            } else {
                showPlayerRounds(nick);
            }
        } else if (command.equals("player") || command.equals("p")){
            if (params.length > 0){
                showPlayerAllStats(params[0]);
            } else {
                showPlayerAllStats(nick);
            }
        } else if (command.equals("paydebt") ) {
            if (!isJoined(nick)) {
                infoNotJoined(nick);
            } else if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 0){
                    try {
                        payPlayerDebt(nick, Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("players")) {
            if (isInProgress()){
                showTablePlayers();
            } else {
                showPlayers();
            }
        } else if (command.equals("waitlist")) {
            showWaitlist();
        } else if (command.equals("blacklist")) {
            showBlacklist();
        } else if (command.equals("top")) {
            if (isInProgress()) {
                infoWaitRoundEnd(nick);
            } else {
                if (params.length > 1){
                    try {
                        showTopPlayers(params[1].toLowerCase(), Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else if (params.length == 1){
                    try {
                        showTopPlayers("cash", Integer.parseInt(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    showTopPlayers("cash", 5);
                }
            }
        } else if (command.equals("grules")) {
            infoGameRules(nick);
        } else if (command.equals("ghelp")) {
            infoGameHelp(nick);
        } else if (command.equals("gcommands")) {
            infoGameCommands(nick);
        } else if (command.equals("game")) {
            showGameName();

        /* Op commands */
        } else if (command.equals("fstart") || command.equals("fgo")){
            if (isForceStartAllowed(user,nick)){
                setInProgress(true);
                showStartRound();
                setStartRoundTask();
            }
        } else if (command.equals("fj") || command.equals("fjoin")){
            if (!channel.isOp(user)) {
                infoOpsOnly(nick);
            } else {
                if (params.length > 0){
                    String fNick = params[0];
                    Set<User> chanUsers = channel.getUsers();
                    Iterator<User> it = chanUsers.iterator();
                    while(it.hasNext()){
                        User u = it.next();
                        if (u.getNick().toLowerCase().equals(fNick.toLowerCase())){
                            join(u.getNick(), u.getHostmask());
                            return;
                        }
                    }
                    infoNickNotFound(nick,fNick);
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("fl") || command.equals("fq") || command.equals("fquit") || command.equals("fleave")){
            if (!channel.isOp(user)) {
                infoOpsOnly(nick);
            } else {
                if (params.length > 0){
                    leave(params[0]);
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("fb") || command.equals("fbet")){
            if (isForceCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        bet(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("fa") || command.equals("fallin")){
            if (isForceCommandAllowed(user, nick)){
                bet(Integer.MAX_VALUE);
            }
        } else if (command.equals("fr") || command.equals("fraise")){
            if (isForceCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        raise(Integer.parseInt(params[0]));
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("fc") || command.equals("fcheck") || command.equals("fcall")){
            if (isForceCommandAllowed(user, nick)){
                checkCall();
            }
        } else if (command.equals("ff") || command.equals("ffold")){
            if (isForceCommandAllowed(user, nick)){
                fold();
            }
        } else if (command.equals("cards") || command.equals("discards")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int num = Integer.parseInt(params[0]);
                        if (command.equals("cards") && deck.getNumberCards() > 0) {
                            infoDeckCards(nick, 'c', num);
                        } else if (command.equals("discards") && deck.getNumberDiscards() > 0) {
                            infoDeckCards(nick, 'd', num);
                        } else {
                            bot.sendNotice(nick, "Empty!");
                        }
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("reload")) {
            if (isOpCommandAllowed(user, nick)){
                loadSettings();
                showReloadSettings();
            }
        } else if (command.equals("set")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 1){
                    try {
                        setSetting(params);
                        showUpdateSetting(params[0]);
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("get")) {
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        showSetting(params[0], getSetting(params[0]));
                    } catch (IllegalArgumentException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }
            }
        } else if (command.equals("test1")){
            if (isOpCommandAllowed(user, nick)){
                // 1. Test if game will properly determine winner of two hands
                Hand h1 = new Hand();
                Hand h2 = new Hand();
                PokerHand ph1 = new PokerHand();
                PokerHand ph2 = new PokerHand();
                Hand comm = new Hand();
                for (int ctr=0; ctr<2; ctr++){
                    dealCard(h1);
                    dealCard(h2);
                }
                for (int ctr=0; ctr<5; ctr++){
                    dealCard(comm);
                }
                ph1.addAll(comm);
                ph1.addAll(h1);
                ph2.addAll(comm);
                ph2.addAll(h2);
                bot.sendMessage(channel, "Hand 1: "+h1.toString());
                bot.sendMessage(channel, "Hand 2: "+h2.toString());
                bot.sendMessage(channel, "Community: "+comm.toString());
                Collections.sort(ph1.getAllCards());
                Collections.reverse(ph1.getAllCards());
                Collections.sort(ph2.getAllCards());
                Collections.reverse(ph2.getAllCards());
                ph1.getValue();
                ph2.getValue();
                bot.sendMessage(channel, "Hand 1, "+ph1.getName()+": " + ph1);
                bot.sendMessage(channel, "Hand 2, "+ph2.getName()+": " + ph2);
                if (ph1.compareTo(ph2) > 0){
                    bot.sendMessage(channel, "Hand 1 wins");
                } else if (ph1.compareTo(ph2) < 0){
                    bot.sendMessage(channel, "Hand 2 wins");
                } else {
                    bot.sendMessage(channel, "Draw");
                }
                deck.addToDiscard(h1.getAllCards());
                deck.addToDiscard(h2.getAllCards());
                deck.addToDiscard(comm.getAllCards());
                deck.refillDeck();
            }
        } else if (command.equals("test2")){	
            // 2. Test if arbitrary hands will be scored properly
            if (isOpCommandAllowed(user, nick)){
                if (params.length > 0){
                    try {
                        int number = Integer.parseInt(params[0]);
                        if (number > 52 || number < 5){
                            throw new NumberFormatException();
                        }
                        PokerHand h = new PokerHand();
                        for (int ctr = 0; ctr < number; ctr++){
                            dealCard(h);
                        }
                        bot.sendMessage(channel, h.toString(0, h.getSize()));
                        Collections.sort(h.getAllCards());
                        Collections.reverse(h.getAllCards());
                        h.getValue();
                        bot.sendMessage(channel, h.getName()+": " + h);
                        deck.addToDiscard(h.getAllCards());
                        deck.refillDeck();
                    } catch (NumberFormatException e) {
                        infoBadParameter(nick);
                    }
                } else {
                    infoNoParameter(nick);
                }

            }
        }
    }
    
    /* Accessor methods */
	public void setMinBet(int value){
    	minBet = value;
    }
    public int getMinBet(){
    	return minBet;
    }
	
    /* Game management methods */
    @Override
	public void addPlayer(String nick, String hostmask) {
		addPlayer(new PokerPlayer(nick, hostmask, false));
	}
	@Override
	public void addWaitlistPlayer(String nick, String hostmask) {
		Player p = new PokerPlayer(nick, hostmask, false);
		waitlist.add(p);
		infoJoinWaitlist(p.getNick());
	}
	public Player getPlayerAfter(Player p){
		return getJoined((getJoinedIndex(p)+1) % getNumberJoined());
	}
	@Override
	public void startRound() {
		if (getNumberJoined() < 2){
			endRound();
		} else {
            setButton();
			showTablePlayers();
			dealTable();
			setBlindBets();
			currentPlayer = getPlayerAfter(bigBlind);
			showTurn(currentPlayer);
			setIdleOutTask();
		}
	}
	@Override
	public void continueRound() {
        // Store currentPlayer as firstPlayer and find the next player
        Player firstPlayer = currentPlayer;
		currentPlayer = getPlayerAfter(currentPlayer);
		PokerPlayer p = (PokerPlayer) currentPlayer;

        /* Look for a player who can bet that is not the firstPlayer or the topBettor.
         * If we reach the firstPlayer or topBettor then stop looking. */
        while ((p.hasFolded() || p.hasAllIn()) && currentPlayer != firstPlayer 
                && currentPlayer != topBettor){
            currentPlayer = getPlayerAfter(currentPlayer);
			p = (PokerPlayer) currentPlayer;
		}
        /* If we reach the firstPlayer or topBettor, then we should deal
         * community cards. */
        if (currentPlayer == topBettor || currentPlayer == firstPlayer) {
			addBetsToPot();
			stage++;
			
            // If all community cards have been dealt, move to end of round
			if (stage == 4){
				endRound();
            // Otherwise, deal community cards
			} else {
                // Burn a card before turn and river
				if (stage != 1){
					burnCard();
				}
				dealCommunity();
				/* Only show dealt community cards when there are 
                 * more than 1 non-folded player remaining. */
				if (getNumberNotFolded() > 1){
					showCommunityCards();
				}
				topBettor = null;
                /* Set the currentPlayer to be dealer to determine who bets
                 * first in the next round of betting. */
				currentPlayer = dealer;
				continueRound();
			}
        // Continue round, if less than 2 players can bet
		} else if (getNumberCanBet() < 2 && topBettor == null){
            continueRound();
        // Continue to the next bettor
        }  else {
			showTurn(currentPlayer);
			setIdleOutTask();
		}
	}
	@Override
	public void endRound() {
		PokerPlayer p;
		if (currentPot != null){
			pots.add(currentPot);
		}
		
        // Check if anybody left during post-start waiting period
		if (getNumberJoined() > 1 && pots.size() > 0) {
			// Give all non-folded players the community cards
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (PokerPlayer) getJoined(ctr);
				if (!p.hasFolded()){
					p.getPokerHand().addAll(p.getHand());
					p.getPokerHand().addAll(community);
					Collections.sort(p.getPokerHand().getAllCards());
					Collections.reverse(p.getPokerHand().getAllCards());
				}
			}
			
			// Determine the winners of each pot
			showResults();
				
			/* Clean-up tasks
             * 1. Increment the number of rounds played for player
             * 2. Remove players who have gone bankrupt and set respawn timers
             * 3. Remove players who have quit mid-round
             * 4. Save player data
             * 5. Reset the player
             */
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (PokerPlayer) getJoined(ctr);
				p.incrementRounds();
                
                // Bankrupts
				if (p.getCash() == 0) {
					p.incrementBankrupts();
					blacklist.add(p);
					infoPlayerBankrupt(p.getNick());
                    removeJoined(p.getNick());
                    setRespawnTask(p);
                    ctr--;
                // Quitters
				} else if (p.hasQuit()) {
					removeJoined(p.getNick());
					ctr--;
                // Remaining players
				} else {
                    savePlayerData(p);
                }
				resetPlayer(p);
			}
		} else {
			showNoPlayers();
		}
		
		resetGame();
		showEndRound();
		showSeparator();
        setInProgress(false);
		mergeWaitlist();
	}
	@Override
	public void endGame() {
		cancelStartRoundTask();
        startRoundTimer.cancel();
		cancelIdleOutTask();
        idleOutTimer.cancel();
		cancelRespawnTasks();
		respawnTimer.cancel();
		saveSettings();
		devoiceAll();
		joined.clear();
		waitlist.clear();
		blacklist.clear();
		deck = null;
		pots.clear();
		currentPlayer = null;
		dealer = null;
		smallBlind = null;
		bigBlind = null;
		topBettor = null;
        showGameEnd();
        bot = null;
        channel = null;
        parentListener = null;
	}
	@Override
	public void resetGame() {
		discardCommunity();
		stage = 0;
		currentBet = 0;
		currentPot = null;
		minRaise = minBet;
		pots.clear();
		currentPlayer = null;
		bigBlind = null;
		smallBlind = null;
		topBettor = null;
		deck.refillDeck();
	}
    @Override
	public void leave(String nick) {
        // Check if the nick is even joined
		if (isJoined(nick)){
			PokerPlayer p = (PokerPlayer) findJoined(nick);
            // Check if a round is in progress
			if (isInProgress()) {
                /* If still in the post-start waiting phase, then currentPlayer has
                 * not been set yet. */
                if (currentPlayer == null){
                    removeJoined(p);
                // Force the player to fold if it is his turn
                } else if (p == currentPlayer){
                    p.setQuit(true);
                    bot.sendNotice(p.getNick(), "You will be removed at the end of the round.");
					fold();
				} else {
                    p.setQuit(true);
					bot.sendNotice(p.getNick(), "You will be removed at the end of the round.");
					if (!p.hasFolded()){
						p.setFold(true);
						showFold(p);
						// Remove this player from any existing pots
						if (currentPot != null && currentPot.hasPlayer(p)){
				            currentPot.removePlayer(p);
				        }
						for (int ctr = 0; ctr < pots.size(); ctr++){
							PokerPot cPot = pots.get(ctr);
							if (cPot.hasPlayer(p)){
								cPot.removePlayer(p);
							}
						}
						// If there is only one player who hasn't folded,
						// force check/call on that remaining player (whose turn it must be)
						if (getNumberNotFolded() == 1){
							checkCall();
						}
					}
				}
            // Just remove the player from the joined list if no round in progress
			} else {
				removeJoined(p);
			}
        // Check if on the waitlist
		} else if (isWaitlisted(nick)) {
			infoLeaveWaitlist(nick);
			removeWaitlisted(nick);
		} else {
			infoNotJoined(nick);
		}
	}
	public void resetPlayer(PokerPlayer p) {
		discardPlayerHand(p);
		p.setFold(false);
		p.setQuit(false);
        p.setAllIn(false);
	}
	public void setButton(){
		if (dealer == null){
			dealer = (PokerPlayer) getJoined(0);
		} else {
			dealer = (PokerPlayer) getPlayerAfter(dealer);
		}
		if (getNumberJoined() == 2){
			smallBlind = dealer;
		} else {
			smallBlind = (PokerPlayer) getPlayerAfter(dealer);
		}
		bigBlind = (PokerPlayer) getPlayerAfter(smallBlind);
	}
	public void setBlindBets(){
		currentBet = Math.min(minRaise, smallBlind.getCash());
		smallBlind.setBet(currentBet);
		currentBet = Math.min(currentBet+minRaise, bigBlind.getCash());
		bigBlind.setBet(currentBet);
	}
    
    /* Game command logic checking methods */
    public boolean isStartCommandAllowed(String nick){
        if (!isJoined(nick)) {
            infoNotJoined(nick);
        } else if (isInProgress()) {
            infoRoundStarted(nick);
        } else if (getNumberJoined() < 2) {
            showNoPlayers();
        } else {
            return true;
        }
        return false;
    }
    public boolean isPlayerTurn(String nick){
        if (!isJoined(nick)){
            infoNotJoined(nick);
        } else if (!isInProgress()) {
            infoNotStarted(nick);
        } else if (findJoined(nick) != currentPlayer){
            infoNotTurn(nick);
        } else {
            return true;
        }
        return false;
    }
    public boolean isForceStartAllowed(User user, String nick){
        if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (isInProgress()) {
            infoRoundStarted(nick);
        } else if (getNumberJoined() < 2) {
            showNoPlayers();
        } else {
            return true;
        }
        return false;
    }
    public boolean isOpCommandAllowed(User user, String nick){
		if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (isInProgress()) {
			infoWaitRoundEnd(nick);
		} else {
            return true;
        }
        return false;
	}
    public boolean isForceCommandAllowed(User user, String nick){
        if (!channel.isOp(user)) {
			infoOpsOnly(nick);
		} else if (!isInProgress()) {
			infoNotStarted(nick);
		} else {
            return true;
        }
        return false;
    }
	
    /* Game settings management */
	@Override
	public void setSetting(String[] params) {
		String setting = params[0].toLowerCase();
		String value = params[1];
		if (setting.equals("idle")) {
			setIdleOutTime(Integer.parseInt(value));
		} else if (setting.equals("cash")) {
			setNewCash(Integer.parseInt(value));
		} else if (setting.equals("respawn")) {
			setRespawnTime(Integer.parseInt(value));
		} else if (setting.equals("minbet")){
			setMinBet(Integer.parseInt(value));
		} else {
			throw new IllegalArgumentException();
		}
		saveSettings();
	}
	@Override
	public String getSetting(String param) {
		if (param.equals("idle")) {
			return getIdleOutTime()+"";
		} else if (param.equals("cash")) {
			return getNewCash()+"";
		} else if (param.equals("respawn")) {
			return getRespawnTime()+"";
		} else if (param.equals("minbet")){
			return getMinBet()+"";
		} else {
			throw new IllegalArgumentException();
		}
	}
	@Override
	public void loadSettings() {
		try {
			BufferedReader in = new BufferedReader(new FileReader("texaspoker.ini"));
			String str, name, value;
			StringTokenizer st;
			while (in.ready()) {
				str = in.readLine();
				if (str.startsWith("#")) {
					continue;
				}
				st = new StringTokenizer(str, "=");
				name = st.nextToken();
				value = st.nextToken();
				if (name.equals("idle")) {
					idleOutTime = Integer.parseInt(value);
				} else if (name.equals("cash")) {
					newcash = Integer.parseInt(value);
				} else if (name.equals("respawn")) {
					respawnTime = Integer.parseInt(value);
				} else if (name.equals("minbet")) {
					minBet = Integer.parseInt(value);
					minRaise = minBet;
				}
			}
			in.close();
		} catch (IOException e) {
			/* load defaults if texaspoker.ini is not found */
			bot.log("texaspoker.ini not found! Creating new texaspoker.ini...");
			newcash = 1000;
			idleOutTime = 60;
			respawnTime = 600;
			minBet = 5;
			minRaise = minBet;
			saveSettings();
		}
	}
	@Override
	public void saveSettings() {
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter("texaspoker.ini")));
			out.println("#Settings");
			out.println("#Number of seconds before a player idles out");
			out.println("idle=" + idleOutTime);
			out.println("#Initial amount given to new and bankrupt players");
			out.println("cash=" + newcash);
			out.println("#Number of seconds before a bankrupt player is allowed to join again");
			out.println("respawn=" + respawnTime);
			out.println("#Minimum betting increment");
			out.println("minbet=" + minBet);
			out.close();
		} catch (IOException f) {
			bot.log("Error creating texaspoker.ini!");
		}
    }
	@Override
    
    /* Player data management */
	public int getTotalPlayers() {
		try {
	    	ArrayList<String> nicks = new ArrayList<String>();
	        ArrayList<Integer> stacks = new ArrayList<Integer>();
	        ArrayList<Integer> bankrupts = new ArrayList<Integer>();
	        ArrayList<Integer> debts = new ArrayList<Integer>();
	        ArrayList<Integer> bjrounds = new ArrayList<Integer>();
	        ArrayList<Integer> tprounds = new ArrayList<Integer>();
	        ArrayList<Boolean> simples = new ArrayList<Boolean>();
	    	loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
	    	int total = 0, numLines = nicks.size();
        	for (int ctr = 0; ctr < numLines; ctr++){
        		if (tprounds.get(ctr) > 0){
        			total++;
        		}
        	}
        	return total;
    	} catch (IOException e){
		 	bot.log("Error reading players.txt!");
		 	return -1;
    	}
	}
	@Override
	public void loadPlayerData(Player p) {
		try {
			boolean found = false;
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			ArrayList<Integer> tprounds = new ArrayList<Integer>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			int numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					if (stacks.get(ctr) <= 0) {
						p.setCash(getNewCash());
					} else {
						p.setCash(stacks.get(ctr));
					}
					p.setDebt(debts.get(ctr));
					p.setBankrupts(bankrupts.get(ctr));
					p.setRounds(tprounds.get(ctr));
					p.setSimple(simples.get(ctr));
					found = true;
					break;
				}
			}
			if (!found) {
				p.setCash(getNewCash());
				p.setDebt(0);
				p.setBankrupts(0);
				p.setRounds(0);
				p.setSimple(true);
				infoNewPlayer(p.getNick());
			}
		} catch (IOException e) {
			bot.log("Error reading players.txt!");
		}
	}
	@Override
	public void savePlayerData(Player p) {
		boolean found = false;
		ArrayList<String> nicks = new ArrayList<String>();
		ArrayList<Integer> stacks = new ArrayList<Integer>();
		ArrayList<Integer> debts = new ArrayList<Integer>();
		ArrayList<Integer> bankrupts = new ArrayList<Integer>();
		ArrayList<Integer> bjrounds = new ArrayList<Integer>();
		ArrayList<Integer> tprounds = new ArrayList<Integer>();
		ArrayList<Boolean> simples = new ArrayList<Boolean>();
		int numLines;
		try {
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			numLines = nicks.size();
			for (int ctr = 0; ctr < numLines; ctr++) {
				if (p.getNick().toLowerCase().equals(nicks.get(ctr).toLowerCase())) {
					stacks.set(ctr, p.getCash());
					debts.set(ctr, p.getDebt());
					bankrupts.set(ctr, p.getBankrupts());
					tprounds.set(ctr, p.getRounds());
					simples.set(ctr, p.isSimple());
					found = true;
                    break;
				}
			}
			if (!found) {
				nicks.add(p.getNick());
				stacks.add(p.getCash());
				debts.add(p.getDebt());
				bankrupts.add(p.getBankrupts());
				bjrounds.add(0);
				tprounds.add(p.getRounds());
				simples.add(p.isSimple());
			}
		} catch (IOException e) {
			bot.log("Error reading players.txt!");
		}

		try {
			savePlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
		} catch (IOException e) {
			bot.log("Error writing to players.txt!");
		}
	}
	
    /* Card management methods for Texas Hold'em Poker */
    public void dealCommunity(){
		if (stage == 1) {
			for (int ctr = 1; ctr <= 3; ctr++){
                dealCard(community);
			}
		} else {
			dealCard(community);
		}
	}
	public void dealHand(PokerPlayer p) {
		Hand h = p.getHand();
		for (int ctr2 = 0; ctr2 < 2; ctr2++) {
            dealCard(h);
		}
	}
	public void dealTable() {
		PokerPlayer p;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++) {
			p = (PokerPlayer) getJoined(ctr);
			dealHand(p);
			infoPlayerHand(p, p.getHand());
		}
	}
    public void discardPlayerHand(PokerPlayer p) {
		if (p.hasHand()) {
			deck.addToDiscard(p.getHand().getAllCards());
			p.resetHand();
		}
	}
	public void discardCommunity(){
        if (community.getSize() > 0){
            deck.addToDiscard(community.getAllCards());
            community.clear();
        }
	}
	
    /* Texas Hold'em Poker gameplay methods */
	public int getNumberNotFolded(){
		PokerPlayer p;
		int numberNotFolded = 0;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (PokerPlayer) getJoined(ctr);
			if (!p.hasFolded()){
				numberNotFolded++;
			}
		}
		return numberNotFolded;
	}
	public int getNumberCanBet(){
		PokerPlayer p;
		int numberCanBet = 0;
		for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (PokerPlayer) getJoined(ctr);
			if (!p.hasFolded() && !p.hasAllIn()){
				numberCanBet++;
			}
		}
		return numberCanBet;
	}
	public void bet (int amount) {
		cancelIdleOutTask();
		PokerPlayer p = (PokerPlayer) currentPlayer;
		int total;
		if (amount == Integer.MAX_VALUE){
			total = p.getCash();
		} else {
			total = amount;
		}
		
		if (total == p.getCash()){
			if (currentBet < total || topBettor == null){
				currentBet = total;
				topBettor = p;
			}
            p.setBet(total);
            p.setAllIn(true);
            showAllIn(p);
			continueRound();
		} else if (total > p.getCash()) {
			bot.sendNotice(p.getNick(), "Not enough cash.");
			setIdleOutTask();
		} else if (total < currentBet) {
			bot.sendNotice(p.getNick(), "Bet too low. Current bet is $" + formatNumber(currentBet)+".");
			setIdleOutTask();
		} else if (total == currentBet){
			p.setBet(total);
			if (topBettor == null){
				topBettor = p;
			}
            if (total == 0){
                showCheck(p);
            } else {
                showCall(p);
            }
			continueRound();
		} else if ((total-currentBet) < minRaise){
			bot.sendNotice(p.getNick(), "Minimum raise is $" + formatNumber(minRaise) + ".");
			setIdleOutTask();
		} else {
			p.setBet(total);
			currentBet = total;
			topBettor = p;
            showRaise(p);
			continueRound();
		}
	}
	public void raise (int amount) {
		cancelIdleOutTask();
		PokerPlayer p = (PokerPlayer) currentPlayer;
		int total = amount + currentBet;
		
		if (total == p.getCash()){
            if (currentBet < total || topBettor == null){
				currentBet = total;
				topBettor = p;
			}
			p.setBet(total);
            p.setAllIn(true);
            showAllIn(p);
			continueRound();
		} else if (total > p.getCash()) {
			bot.sendNotice(p.getNick(), "Not enough cash");
			setIdleOutTask();
		} else if (amount == 0) {
			p.setBet(total);
			if (topBettor == null){
				topBettor = p;
			}
            if (total == 0){
                showCheck(p);
            } else {
                showCall(p);
            }
			continueRound();
		} else if (amount < minRaise) {
			bot.sendNotice(p.getNick(), "Minimum raise is " + formatNumber(minRaise) + ".");
			setIdleOutTask();
		} else {
			p.setBet(total);			
			currentBet = total;
			topBettor = p;
            showRaise(p);
			continueRound();
		}
	}
	public void checkCall(){
		cancelIdleOutTask();
		PokerPlayer p = (PokerPlayer) currentPlayer;
		int total = Math.min(p.getCash(), currentBet);
        
        if (topBettor == null){
            currentBet = total;
			topBettor = p;
		}
		p.setBet(total);
		
        if (total == p.getCash()){
            p.setAllIn(true);
            showAllIn(p);
        } else if (total == 0){
			showCheck(p);
		} else {
			showCall(p);
		}
		continueRound();
	}
	public void fold(){
		cancelIdleOutTask();
		PokerPlayer p = (PokerPlayer) currentPlayer;
		p.setFold(true);
		showFold(p);
        
		//Remove this player from any existing pots
        if (currentPot != null && currentPot.hasPlayer(p)){
            currentPot.removePlayer(p);
        }
		for (int ctr = 0; ctr < pots.size(); ctr++){
			PokerPot cPot = pots.get(ctr);
			if (cPot.hasPlayer(p)){
				cPot.removePlayer(p);
			}
		}
		continueRound();
	}
	public void addBetsToPot(){
		PokerPlayer p;
		while(currentBet != 0){
			int lowBet = currentBet;
            // Create a new pot if none exists
			if (currentPot == null){
				currentPot = new PokerPot();
			} else {
                // Determine if anybody is still in the game, but has not contributed
                // any bets in the latest round of betting, thus requiring a new pot
                for (int ctr = 0; ctr < currentPot.getNumberPlayers(); ctr++){
                    p = currentPot.getPlayer(ctr);
                    if (p.getBet() == 0 && currentBet != 0 && !p.hasFolded() && 
                        currentPot.hasPlayer(p)){
                        pots.add(currentPot);
                        currentPot = new PokerPot();
                        break;
                    }
                }
            }
            // Determine the lowest non-zero bet from a non-folded player
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (PokerPlayer) getJoined(ctr);
				if (p.getBet() < lowBet && p.getBet() != 0 && !p.hasFolded()){
					lowBet = p.getBet();
				}
			}
            // Subtract lowBet from each player's bet and add to pot. For folded
            // players, the min of their bet and lowBet is contributed.
			for (int ctr = 0; ctr < getNumberJoined(); ctr++){
				p = (PokerPlayer) getJoined(ctr);
				if (p.getBet() != 0){
                    if (p.hasFolded()){
                        int bet = Math.min(p.getBet(), lowBet);
                        currentPot.addPot(bet);
                        p.addCash(-1*bet);
                        p.addBet(-1*bet);
                    } else {
                        currentPot.addPot(lowBet);
                        p.addCash(-1*lowBet);
                        p.addBet(-1*lowBet);
                    }
                    // Ensure a non-folded player is included in this pot
                    if (!p.hasFolded() && !currentPot.hasPlayer(p)){
                        currentPot.addPlayer(p);
                    }
				}
			}
			currentBet -= lowBet;
            // If there is any currentBet left over, that means we have to create
            // a new sidepot.
			if (currentBet != 0){
				pots.add(currentPot);
				currentPot = null;
			}
		}
	}
	
    /* Channel message output methods for Texas Hold'em Poker*/
	@Override
	public void showGameStats() {
		int totalPlayers;
		totalPlayers = getTotalPlayers();
        if (totalPlayers == 1){
            bot.sendMessage(channel, formatNumber(totalPlayers)+" player has played " +	getGameNameStr()+".");
        } else {
            bot.sendMessage(channel, formatNumber(totalPlayers)+" players have played " +	getGameNameStr()+".");
        }
	}
    @Override
	public void showTopPlayers(String stat, int n) {
        if (n < 1){
            throw new IllegalArgumentException();
        }
		int highIndex;
		try {
			ArrayList<String> nicks = new ArrayList<String>();
			ArrayList<Integer> stacks = new ArrayList<Integer>();
			ArrayList<Integer> bankrupts = new ArrayList<Integer>();
			ArrayList<Integer> debts = new ArrayList<Integer>();
			ArrayList<Integer> bjrounds = new ArrayList<Integer>();
			ArrayList<Integer> tprounds = new ArrayList<Integer>();
			ArrayList<Boolean> simples = new ArrayList<Boolean>();
			loadPlayerFile(nicks, stacks, debts, bankrupts, bjrounds, tprounds, simples);
			ArrayList<Integer> test = new ArrayList<Integer>();
            int length = Math.min(n, nicks.size());
			String title = Colors.BLACK + ",08Top " + length;
			String list;
			if (stat.equals("cash")) {
				test = stacks;
				title += " Cash:";
			} else if (stat.equals("debt")) {
				test = debts;
				title += " Debt:";
			} else if (stat.equals("bankrupts")) {
				test = bankrupts;
				title += " Bankrupts:";
			} else if (stat.equals("net") || stat.equals("netcash")) {
				for (int ctr = 0; ctr < nicks.size(); ctr++) {
					test.add(stacks.get(ctr) - debts.get(ctr));
				}
				title += " Net Cash:";
			} else if (stat.equals("rounds")) {
				test = tprounds;
				title += " Texas Hold'em Poker Rounds:";
			} else {
				throw new IllegalArgumentException();
			}
			list = title;
            // Find the player with the highest value, add to output string and remove.
            // Repeat n times or for the length of the lists.
			for (int ctr = 1; ctr <= length; ctr++){
				highIndex = 0;
				for (int ctr2 = 0; ctr2 < nicks.size(); ctr2++) {
					if (test.get(ctr2) > test.get(highIndex)) {
						highIndex = ctr2;
					}
				}
				if (stat.equals("rounds") || stat.equals("bankrupts")) {
					list += " #" + ctr + ": " + Colors.WHITE + ",04 "
							+ nicks.get(highIndex) + " " 
							+ formatNumber(test.get(highIndex)) + " "
							+ Colors.BLACK + ",08";
				} else {
					list += " #" + ctr + ": " + Colors.WHITE + ",04 "
							+ nicks.get(highIndex) + " $"
							+ formatNumber(test.get(highIndex)) + " "
							+ Colors.BLACK + ",08";
				}
				nicks.remove(highIndex);
				test.remove(highIndex);
				if (nicks.isEmpty() || ctr == length) {
					break;
				}
                // Output and reset after 10 players
                if (ctr % 10 == 0){
                    bot.sendMessage(channel, list);
                    list = title;
                }
			}
			bot.sendMessage(channel, list);
		} catch (IOException e) {
			bot.log("Error reading players.txt!");
		}
	}
	@Override
	public void showPlayerRounds(String nick) {
		int rounds = getPlayerStat(nick, "tprounds");
		if (rounds != Integer.MIN_VALUE) {
			bot.sendMessage(channel, nick + " has played " + rounds
					+ " round(s) of " + getGameNameStr() + ".");
		} else {
			bot.sendMessage(channel, "No data found for " + nick + ".");
		}
	}
	@Override
    public void showPlayerAllStats(String nick){
        int cash = getPlayerStat(nick, "cash");
        int debt = getPlayerStat(nick, "debt");
        int net = getPlayerStat(nick, "netcash");
        int bankrupts = getPlayerStat(nick, "bankrupts");
        int rounds = getPlayerStat(nick, "tprounds");
        if (cash != Integer.MIN_VALUE) {
            bot.sendMessage(channel, nick+" | Cash: $"+formatNumber(cash)+" | Debt: $"+
                    formatNumber(debt)+" | Net: $"+formatNumber(net)+" | Bankrupts: "+
                    formatNumber(bankrupts)+" | Rounds: "+formatNumber(rounds));
        } else {
            bot.sendMessage(channel, "No data found for " + nick + ".");
        }
    }       
	@Override
	public void showReloadSettings() {
		bot.sendMessage(channel, "texaspoker.ini has been reloaded.");
	}
	public void showTablePlayers(){
		PokerPlayer p;
        String msg = Colors.BOLD+getNumberJoined()+Colors.BOLD+" players: ";
        String nickColor = "";
		for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (PokerPlayer) getJoined(ctr);
            // Give bold to remaining non-folded players
            if (!p.hasFolded()){
                nickColor = Colors.BOLD;
            } else {
                nickColor = "";
            }
            msg += nickColor+p.getNick();

            // Give special players a label
            if (p == dealer || p == smallBlind || p == bigBlind){
                msg += "(";
                if (p == dealer){
                    msg += "D";
                }
                if (p == smallBlind){
                    msg += "S";
                } else if (p == bigBlind){
                    msg += "B";
                }
                msg += ")";
            }
            msg += nickColor;
            if (ctr != getNumberJoined()-1){
                msg += ", ";
            }
		}
		bot.sendMessage(channel, msg);
	}
	public void showCommunityCards(){
        PokerPlayer p;
        StringBuilder msg = new StringBuilder();
        // Append community cards to StringBuilder
        String str = Colors.BOLD+Colors.YELLOW + ",01 Community Cards: " + 
                Colors.NORMAL + " " + community.toString() + " ";
        msg.append(str);
        
        // Append existing pots to StringBuilder
		for (int ctr = 0; ctr < pots.size(); ctr++){
            if (currentPot != pots.get(ctr)){
                str = Colors.YELLOW+",01Pot #"+(ctr+1)+": "+Colors.GREEN+",01$"+formatNumber(pots.get(ctr).getPot())+Colors.NORMAL+" ";
                msg.append(str);
            }
        }

        // Append current pot to StringBuilder
        str = Colors.YELLOW+",01Pot #"+(pots.size()+1)+": "+Colors.GREEN+",01$"+formatNumber(currentPot.getPot())+Colors.NORMAL+" ";
        msg.append(str);
        
        // Append remaining non-folded players
        int notFolded = getNumberNotFolded();
        int count = 0;
        str = "("+Colors.BOLD+notFolded+Colors.BOLD+" players: ";;
        for (int ctr = 0; ctr < getNumberJoined(); ctr++){
			p = (PokerPlayer) getJoined(ctr);
            if (!p.hasFolded()){
                str += p.getNick();
                if (count != notFolded-1){
                    str += ", ";
                }
                count++;
            }
		}
        str += ")";
        msg.append(str);
        
        bot.sendMessage(channel, msg.toString());
	}
    @Override
	public void showTurn(Player p) {
        PokerPlayer pp = (PokerPlayer) p;
		bot.sendMessage(channel, p.getNickStr()+"'s turn. " + p.getNick()+" in for $" + formatNumber(pp.getBet()) + 
                ". Stack: $" + formatNumber(p.getCash()-pp.getBet()) + ". Current bet: $" + formatNumber(currentBet) + ".");
	}
    public void showRaise(PokerPlayer p){
        bot.sendMessage(channel, p.getNickStr()+" has raised to $" + formatNumber(p.getBet())+
					". Stack: $" + formatNumber(p.getCash() - p.getBet()));
    }
    public void showAllIn(PokerPlayer p){
        bot.sendMessage(channel, p.getNickStr()+" has gone all in! " + p.getNick() + " in for $"+
                        formatNumber(p.getBet())+". Stack: $" + formatNumber(p.getCash()-p.getBet()));
    }
    public void showCall(PokerPlayer p){
        bot.sendMessage(channel, p.getNickStr() + " has called. " + p.getNick() + " in for $" +
					formatNumber(p.getBet()) + ". Stack: $" + formatNumber(p.getCash()-p.getBet()));
    }
    public void showCheck(PokerPlayer p){
        bot.sendMessage(channel, p.getNickStr()+" has checked. " + p.getNick()+" in for $" +
					formatNumber(p.getBet()) + ". Stack: $" + formatNumber(p.getCash()-p.getBet()));
    }
	public void showFold(PokerPlayer p){
		bot.sendMessage(channel, p.getNickStr()+" has folded. Stack: $" + formatNumber(p.getCash()-p.getBet()));
	}
	public void showPlayerResult(PokerPlayer p){
		bot.sendMessage(channel, p.getNickStr() + ": "+ p.getPokerHand().getName()+", " + p.getPokerHand() + " (" + p.getHand() + ")");
	}
	public void showResults(){
		ArrayList<PokerPlayer> players;
		PokerPlayer p;
        int winners;
        // Show introduction to end results
        bot.sendMessage(channel, Colors.BOLD+Colors.YELLOW + ",01 Results: " + Colors.NORMAL);
        players = pots.get(0).getPlayers();
        Collections.sort(players);
		Collections.reverse(players);
        // Show each remaining player's hand
        if (pots.get(0).getNumberPlayers() > 1){
            for (int ctr = 0; ctr < players.size(); ctr++){
                p = players.get(ctr);
                showPlayerResult(p);
            }
        }
		for (int ctr = 0; ctr < pots.size(); ctr++){
            winners = 1;
			currentPot = pots.get(ctr);
            players = currentPot.getPlayers();
			Collections.sort(players);
			Collections.reverse(players);
            // Determine number of winners
			for (int ctr2=1; ctr2 < currentPot.getNumberPlayers(); ctr2++){
				if (players.get(0).compareTo(players.get(ctr2)) == 0){
					winners++;
				}
			}
            
            // Output winners
			for (int ctr2=0; ctr2<winners; ctr2++){
				p = players.get(ctr2);
				p.addCash(currentPot.getPot()/winners);
                bot.sendMessage(channel, Colors.YELLOW+",01 Pot #" + (ctr+1) + ": " + Colors.NORMAL + " " + 
                    p.getNickStr() + " wins $" + formatNumber(currentPot.getPot()/winners) + 
                    ". Stack: $" + formatNumber(p.getCash())+ " (" + getPlayerListString(currentPot.getPlayers()) + ")");
			}
		}
	}
	
    /* Private messages to players */
	public void infoPlayerHand(PokerPlayer p, Hand h) {
		if (p.isSimple()) {
			bot.sendNotice(p.getNick(), "Your hand is " + h + ".");
		} else {
			bot.sendMessage(p.getNick(), "Your hand is " + h + ".");
		}
	}
	public void infoBetTooLow(String nick, int min){
        bot.sendNotice(nick, "Minimum bet is $" + formatNumber(min) + ". Try again.");
    }
	public void infoMustAllIn(String nick){
        bot.sendNotice(nick, "You must go all in or fold. Try again.");
    }
	public void infoNoCommunity(String nick){
		bot.sendNotice(nick, "No community cards have been dealt yet.");
	}
	
	@Override
	public String getGameRulesStr() {
		return "This is no limit Texas Hold'em Poker. $" + getMinBet() + " minimum bet/raise.";
	}
	@Override
	public String getGameCommandStr() {
		return "go, join, quit, bet, check, call, " +
               "raise, fold, community, turn, hand, cash, netcash, " + 
			   "debt, paydebt, bankrupts, rounds, player, players, waitlist, " +
               "blacklist, top, simple, stats, game, ghelp, grules, gcommands";
	}
}
