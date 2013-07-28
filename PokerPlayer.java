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

public class PokerPlayer extends Player implements Comparable<PokerPlayer> {
	private Hand hand;
	private PokerHand pHand;
	private int bet;
	private boolean fold, allIn;
	
	/**
     * Creates a new PokerPlayer.
     * Creates the new player with the specified parameters.
     * 
     * @param nick IRC user's nick.
     * @param hostmask IRC user's hostmask.
     */
    public PokerPlayer(String nick, String hostmask){
        super(nick, hostmask, false);
        hand = new Hand();
        pHand = new PokerHand();
        fold = false;
        allIn = false;
        bet = 0;
    }

	public boolean hasHand(){
		return (hand.getSize() > 0);
	}
	public PokerHand getPokerHand(){
		return pHand;
	}
	public Hand getHand(){
		return hand;
	}
	public void resetHand(){
		hand.clear();
		pHand.clear();
		pHand.resetValue();
	}
	
	public void setBet(int b){
		bet = b;
	}
	public void addBet(int b){
		bet += b;
	}
    public int getBet(){
    	return bet;
    }
    public boolean hasBet(){
    	return bet > 0;
    }
    public void clearBet(){
    	bet = 0;
    }
    
    public void setFold(boolean b){
    	fold = b;
    }
    public boolean hasFolded(){
    	return fold;
    }
    public void setAllIn(boolean b){
        allIn = b;
    }
    public boolean hasAllIn(){
        return allIn;
    }
    
    @Override
    public int compareTo(PokerPlayer p){
    	return this.getPokerHand().compareTo(p.getPokerHand());
    }
}
