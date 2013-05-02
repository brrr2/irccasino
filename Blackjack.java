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

import java.util.*;
import java.io.*;

import org.pircbotx.*;
import org.pircbotx.hooks.events.*;

public class Blackjack extends CardGame {
    public static class IdleOutTask extends TimerTask{
        BlackjackPlayer player;
        Blackjack game;
        public IdleOutTask(BlackjackPlayer p, Blackjack g){
            player = p;
            game = g;
        }
        @Override
        public void run(){
            if (player == game.getCurrentPlayer()){
            	player.setIdledOut(true);
                game.bot.sendMessage(game.channel, player.getNickStr()+" has wasted precious time and idled out." );
                if (game.isInProgress() && !game.isBetting()){
                	game.stay();
                } else {
                	game.leaveGame(player.getUser());
                }
            }
        }
    }
    public static class IdleShuffleTask extends TimerTask{
    	Blackjack game;
    	public IdleShuffleTask(Blackjack g){
    		game = g;
    	}
    	@Override
    	public void run(){
    		game.shuffleShoe();
    	}
    }

    private BlackjackPlayer dealer;
    private boolean insuranceBets;
    private int idleOutTime, shoeDecks, respawnTime, newcash, idleShuffleTime;
    private Timer idleShuffleTimer;
    
    /**
     * Class constructor for Blackjack, a subclass of CardGame.
     * 
     * @param parent	the bot that creates an instance of this ListenerAdapter
     * @param gameChannel	the IRC channel in which the game is to be run.
     */
    public Blackjack(PircBotX parent, Channel gameChannel){
        super(parent,gameChannel);
        gameName = "Blackjack";
        dealer = new BlackjackPlayer(bot.getUserBot(),true);
        loadSettings();
        insuranceBets = false;
    }
    
    @Override
    public void onPart(PartEvent event){
        User user = event.getUser();
        leaveGame(user);
    }
    @Override
    public void onQuit(QuitEvent event){
        User user = event.getUser();
        leaveGame(user);
    }
    @Override
    public void onMessage(MessageEvent event){
        String msg = event.getMessage().toLowerCase();
        
        if (msg.charAt(0) == '.'){
            User user = event.getUser();

            /* Parsing commands from the channel */
            if (msg.equals(".join") || msg.equals(".j")){
                if (playerJoined(user)){
                    bot.sendNotice(user,"You have already joined!");
                } else if (isBlacklisted(user)){
                    bot.sendNotice(user, "You have gone bankrupt. Please wait for a loan to join again.");
                } else if (isInProgress()){
                	if (playerWaiting(user)){
                		bot.sendNotice(user, "You have already joined the waitlist!");
                	} else {
                		addWaitingPlayer(user);
                	}
                } else {
                    addPlayer(user);
                }
            } else if (msg.equals(".leave") || msg.equals(".quit") || msg.equals(".l") || msg.equals(".q")){
                if (!playerJoined(user) && !playerWaiting(user)){
                    bot.sendNotice(user,"You are not currently joined or waiting!");
                } else if (playerWaiting(user)){
                	removeWaiting(user);
                } else {
                    leaveGame(user);
                }
            } else if (msg.equals(".start") || msg.equals(".go")){
            	if (!playerJoined(user)){
                    bot.sendNotice(user,"You are not currently joined!");
                } else if (isInProgress()){
                	bot.sendNotice(user,"A round is already in progress!");
                } else if (getNumberPlayers()>0){
                	if (idleShuffleTimer != null){
                		cancelIdleShuffleTimer();
                	}
                	showStartRound();
                    showPlayers();
                    setInProgress(true);
                    setBetting(true);
                    Timer t = new Timer();
                    t.schedule(new StartRoundTask(this), 5000);
                } else {
                    showNoPlayers();
                }
            } else if (msg.startsWith(".bet ") || msg.startsWith(".b ") ||
            			msg.equals(".bet") || msg.equals(".b")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (!isBetting()){
                	bot.sendNotice(user,"No betting in progress!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");                    
                } else {
                	try{
	                	try{
	                        int amount = parseNumberParam(msg);
	                        bet(amount);
	                    } catch (NumberFormatException e){
	                        infoImproperBet(user);
	                    }
                	} catch (NoSuchElementException e){
                		infoNoParameter(user);
                	}
                }
            } else if (msg.equals(".hit") || msg.equals(".h")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	hit();
                }
            } else if (msg.equals(".stay") || msg.equals(".stand") || msg.equals(".sit")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	stay();
                }
            } else if (msg.equals(".doubledown") || msg.equals(".dd")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	doubleDown();
                } 
            } else if (msg.equals(".surrender") || msg.equals (".surr")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	surrender();
                }
            } else if (msg.startsWith(".insure ") || msg.startsWith(".insure")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	try {
	                	try {
	                        int amount = parseNumberParam(msg);
	                        insure(amount);
	                    } catch (NumberFormatException e){
	                        infoImproperBet(user);
	                    }
                	} catch (NoSuchElementException e){
                		infoNoParameter(user);
                	}
                }
            } else if (msg.equals(".split")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
                	bot.sendNotice(user,"No round in progress!");
                } else if (isBetting()){
                	bot.sendNotice(user,"No cards have been dealt yet!");
                } else if (!(currentPlayer == findPlayer(user))){
                	bot.sendNotice(user,"It's not your turn!");
                } else {
                	split();
                }
            } else if (msg.equals(".table")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (isBetting()){
            		bot.sendNotice(user,"No cards have been dealt yet!");
            	} else {
                    showTableHands();
                }
	        } else if (msg.equals(".sum")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (isBetting()){
            		bot.sendNotice(user,"No cards have been dealt yet!");
            	} else {
            		BlackjackPlayer p = (BlackjackPlayer) findPlayer(user);
            		infoPlayerSum(p, p.getCurrentHand());
            	}
            } else if (msg.equals(".hand")){
            	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else if (isBetting()){
            		bot.sendNotice(user,"No cards have been dealt yet!");
            	} else {
            		BlackjackPlayer p = (BlackjackPlayer) findPlayer(user);
            		infoPlayerHand(p, p.getCurrentHand());
            	}
            } else if (msg.equals(".allhands")){
            	bot.sendNotice(user, "This command has not been implemented yet.");
            } else if (msg.equals(".turn")){
	        	if (!playerJoined(user)){
            		bot.sendNotice(user,"You are not currently joined!");
            	} else if (!isInProgress()){
            		bot.sendNotice(user,"No round in progress!");
            	} else {
                    showPlayerTurn(currentPlayer);
                }
            } else if (msg.equals(".simple")){
                if (!playerJoined(user)){
                	bot.sendNotice(user,"You are not currently joined!");
                } else {
                    togglePlayerSimple(user);
                }
            } else if (msg.startsWith(".cash ") || msg.equals(".cash")){
            	try {
	                String nick = parseStringParam(msg);
	                showPlayerCash(nick);
            	} catch (NoSuchElementException e){
            		showPlayerCash(user.getNick());
            	}
            } else if (msg.equals(".top5")){
            	showTopPlayers(5);
            } else if (msg.equals(".players")){
                showPlayers();
            } else if (msg.equals(".waitlist")){
            	showWaiting();
            } else if (msg.equals(".bankrupt")){
            	showBankrupt();
            } else if (msg.equals(".gamerules") || msg.equals(".grules")){
                infoGameRules(user);
            } else if (msg.equals(".gamehelp") || msg.equals(".ghelp")){
                infoGameHelp(user);
            } else if (msg.equals(".gamecommands") || msg.equals(".gcommands")){
                infoGameCommands(user);
            } else if (msg.equals(".currentgame") || msg.equals(".game")){
                showGameName();
            }  else if (msg.equals(".numdecks") || msg.equals(".ndecks")){
                infoNumDecks(user);
            } else if (msg.equals(".numcards") || msg.equals(".ncards")){
            	infoNumCards(user);
            } else if (msg.equals(".numdiscards") || msg.equals(".ndiscards")){
                infoNumDiscards(user);
            } else if (msg.startsWith(".cards") || msg.startsWith(".discards") ||
            			msg.equals(".cards") || msg.equals(".discards")){
                if (channel.isOp(user)){
                	try{
	                	try{
	                		int num = parseNumberParam(msg);
	                		if (msg.startsWith(".cards ") && shoe.getNumberCards() > 0){
	                    		infoDeckCards(user,'c', num);
	                    	} else if (msg.startsWith(".discards ") && shoe.getNumberDiscards() > 0){
	                    		infoDeckCards(user,'d', num);
	                    	} else {
	                    		bot.sendNotice(user,"Empty!");
	                    	}
	                    } catch (NumberFormatException e){
	                    	bot.sendNotice(user,"Bad parameter!");
	                    }
                	} catch (NoSuchElementException e){
                		bot.sendNotice(user,"Parameter missing!");
                	}
                } else {
                    bot.sendNotice(user,"Debugging commands may only be used by ops.");
                }
            } else if (msg.equals(".shuffle")){
            	if (isInProgress()){
            		bot.sendNotice(user,"A round is already in progress! Wait for the round to end.");
            	} else if (channel.isOp(user)){
            		if (idleShuffleTimer != null){
                		cancelIdleShuffleTimer();
                	}
            		shuffleShoe();
            	} else {
                    bot.sendNotice(user,"Debugging commands may only be used by ops.");
                }
            } else if (msg.equals(".reload")){
            	if (isInProgress()){
            		bot.sendNotice(user,"A round is already in progress! Wait for the round to end.");
            	} else if (channel.isOp(user)){
            		if (idleShuffleTimer != null){
                		cancelIdleShuffleTimer();
                	}
            		loadSettings();
            		showReloadSettings();
            	} else {
                    bot.sendNotice(user,"Debugging commands may only be used by ops.");
                }
            }
        }
    }
    
    /* Game management methods */
    @Override
    public void loadSettings(){
    	try {
    		BufferedReader f = new BufferedReader(new FileReader("blackjack.ini"));
    		String str,name,value;
    		StringTokenizer st;
    		while(f.ready()){
    			str = f.readLine();
    			if (str.startsWith("#")){
    				continue;
    			}
    			st = new StringTokenizer(str,"=");
    			name = st.nextToken();
    			value = st.nextToken();
    			if (name.equals("decks")){
    				shoeDecks = Integer.parseInt(value);
    			} else if (name.equals("idle")){
    				idleOutTime = Integer.parseInt(value)*1000;
    			} else if (name.equals("idleshuffle")){
    				idleShuffleTime = Integer.parseInt(value)*1000;
    			} else if (name.equals("cash")){
    				newcash = Integer.parseInt(value);
    			} else if (name.equals("respawn")){
    				respawnTime = Integer.parseInt(value)*1000;
    			}
    		}
    		f.close();
    	} catch (IOException e){
    		/* load defaults if blackjack.ini is not found */
    		System.out.println("Error reading blackjack.ini");
    		shoeDecks = 1;
    		newcash = 1000;
	        idleOutTime = 60000;
	        respawnTime = 600000;
	        idleShuffleTime = 300000;
    	}
    	shoe = new CardDeck(shoeDecks);
        shoe.shuffleCards();
    }
    @Override
    public void leaveGame(User u){
        if (playerJoined(u)){
            Player p = findPlayer(u);
            if (isInProgress()){
                if (p == currentPlayer){
                    currentPlayer = getNextPlayer();
                    removePlayer(u);
                    if (currentPlayer == null){
                        if (isBetting()){
                            setBetting(false);
                            if (getNumberPlayers()==0){
                                endRound();
                            } else {
                                dealTable();
                                currentPlayer = players.get(0);
                                quickEval();
                            }
                        } else {
                            endRound();
                        }
                    } else {
                        showPlayerTurn(currentPlayer);
                    }
                } else {
                    removePlayer(u);
                }
            } else {
                removePlayer(u);
            }
        }
    }
    @Override
    public void startRound(){
    	if (getNumberPlayers() > 0){
	        currentPlayer = players.get(0);
	        showPlayerTurn(currentPlayer);
	        setIdleOutTimer();
    	} else {
    		endRound();
    	}
    }
    @Override
    public void endRound(){
        Player p;
        Hand dHand;
        setInProgress(false);
        if (getNumberPlayers()>0){
            showPlayerTurn(dealer);
            dHand = dealer.getCurrentHand();
            showPlayerHand(dealer, dHand, true);
            if (needDealerHit()){
	            while(getCardSum(dHand)<17){
	            	dealOne(dHand);
	                showPlayerHand(dealer, dHand, true);
	            }
            }
            if (isHandBlackjack(dHand)){
            	showBlackjack(dealer);
            } else if (isHandBusted(dHand)){
            	showBusted(dealer);
            }
            showResults();
            if (hasInsuranceBets()){
            	showInsuranceResults();
            }
            for (int ctr=0; ctr<getNumberPlayers(); ctr++){
                p = getPlayer(ctr);
                if(isPlayerBankrupt(p)){
                    blacklist.add(p);
                    infoPlayerBankrupt(p.getUser());
                    bot.sendMessage(channel, p.getNickStr()+" has gone bankrupt. S/He has been kicked to the curb.");
                    removePlayer(p.getUser());
                    Timer t = new Timer();
                    t.schedule(new RespawnTask(p,this), respawnTime);
                    ctr--;
                }
            }
        } else {
            showNoPlayers();
        }
        resetGame();
        removeIdlers();
        showEndRound();
        showSeparator();
        addWaitingPlayers();
        if (shoe.getNumberDiscards() > 0){
        	setIdleShuffleTimer();
        }
    }
    @Override
    public void endGame(){
        Player p;
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = getPlayer(ctr);
            savePlayerData(p);
        }
        for (int ctr=0; ctr<getNumberWaiting(); ctr++){
            p = getWaiting(ctr);
            savePlayerData(p);
        }
        for (int ctr=0; ctr<getNumberBankrupt(); ctr++){
            p = getBankrupt(ctr);
            savePlayerData(p);
        }
        players.clear();
        waitlist.clear();
        blacklist.clear();
        shoe = null;
        dealer = null;
        currentPlayer = null;
    }
    @Override
    public void resetGame(){
        discardPlayerHand(dealer);
        setInsuranceBets(false);
        resetPlayers();
    }
    @Override
    public void resetPlayers(){
        BlackjackPlayer p;
        for (int ctr = 0; ctr < getNumberPlayers(); ctr++){
            p = (BlackjackPlayer) players.get(ctr);
            discardPlayerHand(p);
            p.resetCurrentIndex();
            p.clearInitialBet();
        }
    }
    @Override
    public void setIdleOutTimer(){
        idleOutTimer = new Timer();
        idleOutTimer.schedule(new IdleOutTask((BlackjackPlayer) currentPlayer, this), idleOutTime);
    }
    @Override
    public void cancelIdleOutTimer(){
        idleOutTimer.cancel();
    }
    public void setIdleShuffleTimer(){
    	idleShuffleTimer = new Timer();
    	idleShuffleTimer.schedule(new IdleShuffleTask(this), idleShuffleTime);
    }
    public void cancelIdleShuffleTimer(){
    	idleShuffleTimer.cancel();
    }
    
    public void removeIdlers(){
    	Player p;
    	for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = getPlayer(ctr);
            if(isPlayerIdledOut(p)){
            	leaveGame(p.getUser());
                ctr--;
            }
        }
    }
    
    /* Player management methods */
    @Override
    public void addPlayer(User user){
        Player p = new BlackjackPlayer(user,false);
        players.add(0,p);
        loadPlayerData(p);
        showPlayerJoin(p);
    }
    @Override
    public void addWaitingPlayer(User user){
    	Player p = new BlackjackPlayer(user,false);
    	waitlist.add(p);
    	loadPlayerData(p);
    	infoPlayerWaiting(p);
    }
    
    /* Calculations for blackjack */
    public int getCardValue(Card c){
        int num;
        try { 
            num = Integer.parseInt(c.getFace()); 
        } catch(NumberFormatException e) { 
            return 10;
        }
        return num;
    }
    public int getCardSum(Hand h){
        int sum = 0, numAces=0;
        int numCards = h.getSize();
        Card card;
        //sum the non-aces first and store number of aces in the hand
        for (int ctr = 0; ctr < numCards; ctr++){
        	card = h.get(ctr);
            if (card.getFace().equals("A")){
                numAces++;
            } else {
                sum += getCardValue(card);
            }
        }
        //find biggest non-busting sum with aces
        if (numAces > 0){
            if ((numAces-1)+11+sum>21){
                sum += numAces;
            } else {
                sum += (numAces-1)+11;
            }
        }
        return sum;
    }
    public int calcHalf(int amount){
        return (int)(Math.ceil((double)(amount)/2.));
    }
    
    /* Card management methods for Blackjack */
    public void shuffleShoe(){
    	shoe.refillDeck();
    	showShuffleShoe();
    }
    public void dealOne(Hand h){
    	h.add(shoe.takeCard());
    	if (shoe.getNumberCards() == 0){
    		showDeckEmpty();
    		shoe.refillDeck();
    	}
    }
    public void dealHand(BlackjackPlayer p){
    	p.addHand();
    	Hand h = p.getCurrentHand();
    	for (int ctr2=0; ctr2<2; ctr2++){
        	h.add(shoe.takeCard());
        	if (shoe.getNumberCards() == 0){
        		showDeckEmpty();
        		shoe.refillDeck();
        	}
        }
    }
    public void dealTable(){
        BlackjackPlayer p;
        Hand h;
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = (BlackjackPlayer) players.get(ctr);
            dealHand(p);
            h = p.getCurrentHand();
            h.setBet(p.getInitialBet());
            if (shoe.getNumberDecks() == 1){
            	infoPlayerHand(p, h);
            }
        }
        dealHand(dealer);
        showTableHands();
    }
    @Override
    public void discardPlayerHand(Player p){
    	BlackjackPlayer BJp = (BlackjackPlayer) p;
        if (BJp.hasHands()){
        	for (int ctr = 0; ctr < BJp.getNumberHands(); ctr++){
        		shoe.addToDiscard(BJp.getHand(ctr).getAllCards());
        	}
        	BJp.resetHands();
        }
    }
    
    /* Direct Blackjack command methods */
    public void bet(int amount){
        cancelIdleOutTimer();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        if (amount > p.getCash()){
            infoBetTooHigh(p);
            setIdleOutTimer();
        } else if (amount <= 0){
            infoBetTooLow(p);
            setIdleOutTimer();
        } else {
            p.setInitialBet(amount);
            p.addCash(-1*amount);
            showProperBet(p);
            currentPlayer = getNextPlayer();
            if (currentPlayer == null){
                setBetting(false);
                showDealingTable();
                dealTable();
                currentPlayer = players.get(0);
                quickEval();
            } else {
                showPlayerTurn(currentPlayer);
                setIdleOutTimer();
            }
        }
    }
    public void stay(){
        cancelIdleOutTimer();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        Hand nHand;
        if (p.getNumberHands() > 1 && p.getCurrentIndex() < p.getNumberHands()-1){
        	nHand = p.getNextHand();
        	showPlayerHand(p, nHand, p.getCurrentIndex()+1);
        	quickEval();
        } else {
	        currentPlayer = getNextPlayer();
	        if (currentPlayer == null){
	            endRound();
	        } else {
	            quickEval();
	        }
        }
    }
    public void hit(){
        cancelIdleOutTimer();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        Hand nHand, cHand = p.getCurrentHand();
        dealOne(cHand);
        if (p.hasSplit()){
        	showPlayerHand(p, cHand, p.getCurrentIndex()+1);
        } else {
        	showPlayerHand(p, cHand);
        }
        if (isHandBusted(cHand)){
        	showBusted(p);
        	if (p.getNumberHands() > 1 && p.getCurrentIndex() < p.getNumberHands()-1){
        		nHand = p.getNextHand();
            	showPlayerHand(p, nHand, p.getCurrentIndex()+1);
            	quickEval();
        	} else {
	            currentPlayer = getNextPlayer();
	            if (currentPlayer == null){
	                endRound();
	            } else {
	                quickEval();
	            }
        	}
        } else {
            setIdleOutTimer();
        }
    }
    public void doubleDown(){
        cancelIdleOutTimer();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        Hand nHand, cHand = p.getCurrentHand();
        if (cHand.hasHit()){
        	infoNotDoubleDown(p);
        	setIdleOutTimer();
        } else if (p.getInitialBet() > p.getCash()){
            infoInsufficientFunds(p);
            setIdleOutTimer();
        } else {
            p.addCash(-1*cHand.getBet());
            cHand.addBet(cHand.getBet());
            dealOne(cHand);
            showDoubleDown(p, cHand);
            if (p.hasSplit()){
            	showPlayerHand(p, cHand, p.getCurrentIndex()+1);
            } else {
            	showPlayerHand(p, cHand);
            }
            if (isHandBusted(cHand)){
            	showBusted(p);
            }
            if (p.getNumberHands() > 1 && p.getCurrentIndex() < p.getNumberHands()-1){
        		nHand = p.getNextHand();
            	showPlayerHand(p, nHand, p.getCurrentIndex()+1);
            	quickEval();
        	} else {
	            currentPlayer = getNextPlayer();
	            if (currentPlayer == null){
	                endRound();
	            } else {
	                quickEval();
	            }
        	}
        }
    }
    public void surrender(){
        cancelIdleOutTimer();
        BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
        Hand nHand, cHand = p.getCurrentHand();
        if (cHand.hasHit()){
            infoNotSurrender(p);
            setIdleOutTimer();
        } else {
            p.addCash(calcHalf(cHand.getBet()));
            cHand.setSurrender(true);
            showSurrender(p);
            if (p.getNumberHands() > 1 && p.getCurrentIndex() < p.getNumberHands()-1){
        		nHand = p.getNextHand();
            	showPlayerHand(p, nHand, p.getCurrentIndex()+1);
            	quickEval();
        	} else {
	            currentPlayer = getNextPlayer();
	            if (currentPlayer == null){
	                endRound();
	            } else {
	                quickEval();
	            }
        	}
        }
    }
    public void insure(int amount){
    	cancelIdleOutTimer();
    	BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
    	Hand cHand = p.getCurrentHand();
    	if (cHand.hasInsured()){
			infoAlreadyInsured(p);
		} else if (!dealerUpcardAce()){
    		infoNotInsure(p);
    	} else if (p.getCash() == 0){
            infoInsufficientFunds(p);
        } else if (amount > calcHalf(cHand.getBet())){
			infoInsureBetTooHigh(p);
		} else if (amount <= 0){
			infoBetTooLow(p);
		} else {
			setInsuranceBets(true);
			cHand.setInsureBet(amount);
			p.addCash(-1*amount);
			showInsure(p, cHand);
		}
    	setIdleOutTimer();
    }
    public void split(){
    	cancelIdleOutTimer();
    	BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
    	Hand nHand, cHand = p.getCurrentHand();
    	if (!isHandPair(cHand)){
    		infoNotPair(p);
    		setIdleOutTimer();
    	} else if (p.getCash() < cHand.getBet()){
    		infoInsufficientFunds(p);
    		setIdleOutTimer();
    	} else {
    		p.addCash(-1*cHand.getBet());
    		p.splitHand();
    		dealOne(cHand);
    		nHand = p.getHand(p.getCurrentIndex()+1);
    		dealOne(nHand);
    		nHand.setBet(cHand.getBet());
    		showSplitHands(p);
    		showSeparator();
    		showPlayerHand(p,cHand,p.getCurrentIndex()+1);
    		quickEval();
    	}
    }
    
    /* Blackjack behind-the-scenes methods */
    public void quickEval(){
    	BlackjackPlayer p = (BlackjackPlayer) currentPlayer;
    	Hand cHand = p.getCurrentHand();
    	if (p.hasSplit()) {
    		showPlayerTurn(p, p.getCurrentIndex()+1);
    	} else {
    		showPlayerTurn(p);
    	}
        
        if (isHandBusted(cHand)){
        	showBusted(p);
        	if (p.getNumberHands() > 1 && p.getCurrentIndex() < p.getNumberHands()-1){
        		cHand = p.getNextHand();
        		showPlayerHand(p, cHand, p.getCurrentIndex()+1);
        	} else{
	            currentPlayer = getNextPlayer();
	            if (currentPlayer == null){
	                endRound();
	                return;
	            }
        	}
            showPlayerTurn(currentPlayer);
        } else if (isHandBlackjack(cHand)){
        	showBlackjack(p);
        }
        setIdleOutTimer();
    }
    public boolean isHandPair(Hand h){
    	if (h.getSize() > 2){
    		return false;
    	}
    	return h.get(0).getFace().equals(h.get(1).getFace());
    }
    public boolean isHandBlackjack(Hand h){
    	int sum = getCardSum(h);
    	if (sum == 21 && h.getSize() == 2){
    		return true;
    	}
    	return false;
    }
    public boolean isHandBusted(Hand h){
        int sum = getCardSum(h);
        if (sum > 21){
            return true;
        }
        return false;
    }
    public int evaluateHand(Hand h){
    	Hand dHand = dealer.getCurrentHand();
        int sum = getCardSum(h), dsum = getCardSum(dHand);
        boolean pBlackjack = isHandBlackjack(h);
        boolean dBlackjack = isHandBlackjack(dHand);
        if (sum > 21){
            return -1;
        } else if (sum == 21){
        	/* Different cases at 21 */
        	if (pBlackjack && !dBlackjack){
                 return 2;
            } else if (pBlackjack && dBlackjack){
                return 0;
            } else if (!pBlackjack && dBlackjack){
            	return -1;
            } else {
            	if (dsum == 21){
	                return 0;
            	} else {
	                return 1;
            	}
            }
        } else {
        	/* Any case other than 21 */
            if (dsum > 21 || dsum < sum){
                return 1;
            } else if (dsum == sum){
                return 0;
            } else {
                return -1;
            }       
        }
    }
    public int evaluateInsurance(){
    	Hand dHand = dealer.getCurrentHand();
    	if (isHandBlackjack(dHand)){
    		return 1;
    	} else {
    		return -1;
    	}
    }
    public boolean dealerUpcardAce(){
    	if (dealer.hasHands()){
    		Hand dHand = dealer.getCurrentHand();
    		if (dHand.get(1).getFace().equals("A")){
    			return true;
    		} else {
    			return false;
    		}
    	}
    	return false;
    }
    public boolean hasInsuranceBets(){
    	return insuranceBets;
    }
    public void setInsuranceBets(boolean b){
    	insuranceBets = b;
    }
    public boolean needDealerHit(){
    	for (int ctr=0; ctr<getNumberPlayers(); ctr++){
    		BlackjackPlayer p = (BlackjackPlayer) getPlayer(ctr);
    		for (int ctr2=0; ctr2<p.getNumberHands(); ctr2++){
    			Hand h = p.getHand(ctr2);
    			if (!isHandBusted(h) && !h.hasSurrendered() && !isHandBlackjack(h)){
    				return true;
    			}
    		}
    	}
    	return false;
    }
    
    /* Channel output methods for Blackjack */
    @Override
    public void showPlayerTurn(Player p){
        if (isBetting()){
            bot.sendMessage(channel, p.getNickStr()+"'s turn. Stack: $"+p.getCash()+". Enter an initial bet up to $"+p.getCash()+".");
        } else {
            bot.sendMessage(channel,"It's now "+p.getNickStr()+"'s turn.");
        }
    }
    public void showPlayerTurn(Player p, int index){
        if (isBetting()){
            bot.sendMessage(channel, p.getNickStr()+"-"+index+"'s turn. Stack: $"+p.getCash()+". Enter an initial bet up to $"+p.getCash()+".");
        } else {
            bot.sendMessage(channel,"It's now "+p.getNickStr()+"-"+index+"'s turn.");
        }
    }
    public void showPlayerHand(BlackjackPlayer p, Hand h, boolean nohole){
    	if (nohole) {
     		bot.sendMessage(channel, p.getNickStr()+": "+h.toString(0));
     	} else if (p.isDealer() || shoe.getNumberDecks() == 1){
    		bot.sendMessage(channel, p.getNickStr()+": "+h.toString(1));
    	} else {
    		bot.sendMessage(channel, p.getNickStr()+": "+h.toString(0));
    	}
    }
    public void showPlayerHand(BlackjackPlayer p, Hand h){
    	if (p.isDealer() || shoe.getNumberDecks() == 1){
    		bot.sendMessage(channel, p.getNickStr()+": "+h.toString(1));
    	} else {
    		bot.sendMessage(channel, p.getNickStr()+": "+h.toString(0));
    	}
    }
    public void showPlayerHand(BlackjackPlayer p, Hand h, int handIndex){
    	if (p.isDealer() || shoe.getNumberDecks() == 1){
    		bot.sendMessage(channel, p.getNickStr()+"-"+handIndex+": "+h.toString(1));
    	} else {
    		bot.sendMessage(channel, p.getNickStr()+"-"+handIndex+": "+h.toString(0));
    	}
    }
    public void showPlayerHandWithBet(BlackjackPlayer p, Hand h, int handIndex){
    	if (p.isDealer() || shoe.getNumberDecks() == 1){
    		bot.sendMessage(channel, p.getNickStr()+"-"+handIndex+": "+h.toString(1)+", bet: $"+h.getBet());
    	} else {
    		bot.sendMessage(channel, p.getNickStr()+"-"+handIndex+": "+h.toString(0)+", bet: $"+h.getBet());
    	}
    }
    public void showDeckEmpty(){
    	bot.sendMessage(channel,"The dealer's shoe is empty. Refilling the dealer's shoe...");
    }    
    public void showProperBet(BlackjackPlayer p){
        bot.sendMessage(channel,p.getNickStr()+" bets $"+p.getInitialBet()+". Stack: $"+p.getCash());
    }
    public void showDoubleDown(BlackjackPlayer p, Hand h){
        bot.sendMessage(channel, p.getNickStr()+" has doubled down! The bet is now $"+h.getBet()+". Stack: $"+p.getCash());
    }
    public void showSurrender(BlackjackPlayer p){
        bot.sendMessage(channel, p.getNickStr()+" has surrendered! Half the bet is returned and the rest forfeited. Stack: $"+p.getCash());
    }
    public void showInsure(BlackjackPlayer p, Hand h){
    	bot.sendMessage(channel, p.getNickStr()+" has made an insurance bet of $"+h.getInsureBet()+". Stack: $"+p.getCash());
    }
    public void showSplitHands(BlackjackPlayer p){
    	Hand h;
    	bot.sendMessage(channel, p.getNickStr()+" has split the hand! "+p.getNickStr()+"'s hands are now:");
    	for (int ctr=0; ctr<p.getNumberHands(); ctr++){
    		h = p.getHand(ctr);
    		showPlayerHandWithBet(p, h, ctr+1);
    	}
    	bot.sendMessage(channel, p.getNickStr()+"'s stack: $"+p.getCash());
    }
    public void showShuffleShoe(){
    	bot.sendMessage(channel, "The dealer's shoe has been shuffled.");
    }
    public void showReloadSettings(){
    	bot.sendMessage(channel, "blackjack.ini has been reloaded.");
    }
    public void showSeparator(){
    	bot.sendMessage(channel, Colors.BOLD+"------------------------------------------------------------------");
    }
    public void showDealingTable(){
    	bot.sendMessage(channel, Colors.BOLD+Colors.DARK_GREEN+"Dealing..."+Colors.NORMAL);
    }
    public void showBusted(BlackjackPlayer p){
        bot.sendMessage(channel, p.getNickStr()+" has busted!");
    }
    public void showBlackjack(BlackjackPlayer p){
        bot.sendMessage(channel, p.getNickStr()+" has blackjack!");
    }
    public void showTableHands(){
        BlackjackPlayer p;
        bot.sendMessage(channel,Colors.DARK_GREEN+Colors.BOLD+"Table:"+Colors.NORMAL);
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = (BlackjackPlayer) players.get(ctr);
            showPlayerHand(p, p.getCurrentHand());
        }
        showPlayerHand(dealer, dealer.getCurrentHand());
    }
    public void showInsuranceResults(){
    	BlackjackPlayer p;
    	Hand h, dHand = dealer.getCurrentHand();
    	
    	bot.sendMessage(channel, Colors.BOLD+Colors.DARK_GREEN+"Insurance Results:"+Colors.NORMAL);
    	
    	if (isHandBlackjack(dHand)){
    		bot.sendMessage(channel,dealer.getNickStr()+" had blackjack.");
    	} else {
    		bot.sendMessage(channel,dealer.getNickStr()+" did not have blackjack.");
    	}
    	
    	for (int ctr=0; ctr<getNumberPlayers(); ctr++){
    		p = (BlackjackPlayer) getPlayer(ctr);
    		for (int ctr2=0; ctr2<p.getNumberHands(); ctr2++){
    			h = p.getHand(ctr2);
	    		if (h.hasInsured()){
	    			if (p.hasSplit()){
	    				showPlayerInsuranceResult(p, h, ctr2+1);
	    			} else {
	    				showPlayerInsuranceResult(p, h);
	    			}
	    		}
    		}
    	}
    }
    public void showResults(){
        BlackjackPlayer p;
        Hand h;
        bot.sendMessage(channel, Colors.BOLD+Colors.DARK_GREEN+"Results:"+Colors.NORMAL);
        showDealerResult();
        for (int ctr=0; ctr<getNumberPlayers(); ctr++){
            p = (BlackjackPlayer) getPlayer(ctr);
            for (int ctr2=0; ctr2<p.getNumberHands(); ctr2++){
            	h = p.getHand(ctr2);
            	if (p.hasSplit()){
            		showPlayerResult(p, h, ctr2+1);
            	} else {
            		showPlayerResult(p, h);
            	}
            }
        }
    }
    public void showDealerResult(){
    	Hand dHand = dealer.getCurrentHand();
        int sum = getCardSum(dHand);
        String outStr = dealer.getNickStr();
        if (sum > 21){
        	outStr += " has busted! (";
        } else if (isHandBlackjack(dHand)){
        	outStr += " has blackjack! (";
        } else {
        	outStr += " has "+sum+". (";
        }
        outStr += dHand.toString(0)+")";
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerResult(BlackjackPlayer p, Hand h){
        String outStr = p.getNickStr();
        int result = evaluateHand(h);
        if (h.hasSurrendered()){
            outStr += " surrendered. (";
        } else if (result == 2){
        	p.addCash(2*h.getBet()+calcHalf(h.getBet()));
        	outStr += " wins $"+(2*h.getBet()+calcHalf(h.getBet()))+". (";
        } else if (result == 1){
        	p.addCash(2*h.getBet());
            outStr += " wins $"+(2*h.getBet())+". (";
        } else if (result == 0){
        	p.addCash(h.getBet());
            outStr += " pushes and his/her $"+h.getBet()+" bet is returned. (";
        } else {
            outStr += " loses. (";    
        }
        outStr += h.toString(0)+") Stack: $"+p.getCash();
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerResult(BlackjackPlayer p, Hand h, int index){
        String outStr = p.getNickStr()+"-"+index;
        int result = evaluateHand(h);
        if (h.hasSurrendered()){
            outStr += " surrendered. (";
        } else if (result == 2){
        	p.addCash(2*h.getBet()+calcHalf(h.getBet()));
        	outStr += " wins $"+(2*h.getBet()+calcHalf(h.getBet()))+". (";
        } else if (result == 1){
        	p.addCash(2*h.getBet());
            outStr += " wins $"+(2*h.getBet())+". (";
        } else if (result == 0){
        	p.addCash(h.getBet());
            outStr += " pushes and his/her $"+h.getBet()+" bet is returned. (";
        } else {
            outStr += " loses. (";    
        }
        outStr += h.toString(0)+") Stack: $"+p.getCash();
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerInsuranceResult(BlackjackPlayer p, Hand h){
    	String outStr;
        int result = evaluateInsurance();
        if (result == 1){
        	p.addCash(3*h.getInsureBet());
            outStr = p.getNickStr()+" wins $"+3*h.getInsureBet()+".";
        } else {
            outStr = p.getNickStr()+" loses.";
        }
        outStr += " Stack: $"+p.getCash();
        bot.sendMessage(channel, outStr);
    }
    public void showPlayerInsuranceResult(BlackjackPlayer p, Hand h, int index){
    	String outStr;
        int result = evaluateInsurance();
        if (result == 1){
        	p.addCash(3*h.getInsureBet());
            outStr = p.getNickStr()+"-"+index+" wins $"+3*h.getInsureBet()+".";
        } else {
            outStr = p.getNickStr()+"-"+index+" loses.";
        }
        outStr += " Stack: $"+p.getCash();
        bot.sendMessage(channel, outStr);
    }
    
    /* Player/User output methods to simplify messaging/noticing */
    public void infoNoParameter(User user){
    	bot.sendNotice(user, "Parameter missing!");
    }
    public void infoNotPair(BlackjackPlayer p){
    	bot.sendNotice(p.getUser(), "Your hand cannot be split. The cards do not have matching faces.");
    }
    public void infoImproperBet(User user){
        bot.sendNotice(user,"Improper bet. Try again.");
    }
    public void infoBetTooLow(BlackjackPlayer p){
        bot.sendNotice(p.getUser(), "Minimum bet is $1. Try again.");
    }
    public void infoBetTooHigh(BlackjackPlayer p){
        bot.sendNotice(p.getUser(), "Maximum bet is $"+p.getCash()+". Try again.");
    }
    public void infoInsureBetTooHigh(BlackjackPlayer p){
    	bot.sendNotice(p.getUser(), "Maximum insurance bet is $"+calcHalf(p.getInitialBet())+". Try again.");
    }
    public void infoInsufficientFunds(BlackjackPlayer p){
    	bot.sendNotice(p.getUser(), "Insufficient funds. Try again.");
    } 
    public void infoNotDoubleDown(BlackjackPlayer p){
        bot.sendNotice(p.getUser(), "You can only double down before hitting!");
    }
    public void infoNotSurrender(BlackjackPlayer p){
        bot.sendNotice(p.getUser(), "You can only surrender before hitting!");
    }
    public void infoNotInsure(BlackjackPlayer p){
    	bot.sendNotice(p.getUser(), "The dealer's upcard is not an ace. You cannot make an insurance bet.");
    }
    public void infoAlreadyInsured(BlackjackPlayer p){
    	bot.sendNotice(p.getUser(), "You have already made an insurance bet.");
    }
    public void infoPlayerHand(BlackjackPlayer p, Hand h){
        if (p.isSimple()){
            bot.sendNotice(p.getUser(), "Your current hand is "+h.toString(0)+".");
        } else {
            bot.sendMessage(p.getUser(), "Your current hand is "+h.toString(0)+".");
        }
    }
    public void infoPlayerSum(BlackjackPlayer p, Hand h){
        if (p.isSimple()){
            bot.sendNotice(p.getUser(),"Current sum is "+getCardSum(h)+".");
        } else {
            bot.sendMessage(p.getUser(),"Current sum is "+getCardSum(h)+".");
        }
    }
    public void infoPlayerBet(BlackjackPlayer p, Hand h){
        String outStr = "This hand has a bet $"+h.getBet();
        if (h.hasInsured()){
        	outStr += " with an insurance bet of $"+h.getInsureBet();
        }
        outStr += ".";
        if (p.isSimple()){
            bot.sendNotice(p.getUser(), outStr);
        } else {
            bot.sendMessage(p.getUser(), outStr);
        }
    }
    public void infoPlayerBankrupt(User user){
        Player p = findPlayer(user);
        if (p.isSimple()){
            bot.sendNotice(p.getUser(), "You've lost all your money. Please wait "+respawnTime/60000+" minutes for a loan.");
        } else {
            bot.sendMessage(p.getUser(), "You've lost all your money. Please wait "+respawnTime/60000+" minutes for a loan.");
        }
    }
    @Override
    public void infoGameRules(User user){
    	if (playerJoined(user)){
			Player p = findPlayer(user);
		    if (p.isSimple()){
		    	bot.sendNotice(user, "Dealer stands on soft 17. The dealer's shoe has "+shoe.getNumberDecks()+" decks of cards. Cards are shuffled when the shoe is depleted.");
		    	bot.sendNotice(user, "Regular wins are paid out at 1:1 and blackjacks are paid out at 3:2. Insurance wins are paid out at 2:1");
		    } else {
		    	bot.sendMessage(user, "Dealer stands on soft 17. The dealer's shoe has "+shoe.getNumberDecks()+" decks of cards. Cards are reshuffled when the shoe is depleted.");
		    	bot.sendMessage(user, "Regular wins are paid out at 1:1 and blackjacks are paid out at 3:2. Insurance wins are paid out at 2:1");
		    }
    	} else {
    		bot.sendMessage(user, "Dealer stands on soft 17. The dealer's shoe has "+shoe.getNumberDecks()+" decks of cards. Cards are shuffled when the shoe is depleted.");
	    	bot.sendMessage(user, "Regular wins are paid out at 1:1 and blackjacks are paid out at 3:2. Insurance wins are paid out at 2:1. Early surrender is offered.");
    	}
    }
    @Override
    public void infoGameHelp(User user){
    	if (playerJoined(user)){
			Player p = findPlayer(user);
		    if (p.isSimple()){
		    	bot.sendNotice(user, "For help on how to play "+getGameNameStr()+", please visit an online resource. For game commands, type .gcommands.");
		    } else {
		    	bot.sendMessage(user, "For help on how to play "+getGameNameStr()+", please visit an online resource. For game commands, type .gcommands.");
		    }
    	} else {
    		bot.sendMessage(user, "For help on how to play "+getGameNameStr()+", please visit an online resource. For game commands, type .gcommands. For house rules, type .grules.");
    	}
    }
    
    
    /* Formatted strings */
    @Override
    public String getGameNameStr(){
    	return Colors.BOLD+gameName+Colors.NORMAL;
    }
    @Override
    public String getGameCommandStr(){
    	return "start (go), join (j), leave (quit, l, q), bet (b), hit (h), stay (stand), doubledown (dd), surrender, " +
    			"insure, split, table, turn, sum, cash, hand, allhands, simple, players, waitlist, " +
    			"gamehelp (ghelp), gamerules (grules), gamecommands (gcommands)";
    }
}