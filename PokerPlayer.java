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

/**
 * Extends the Player class for players playing Poker games.
 * @author Yizhe Shen
 */
public class PokerPlayer extends Player implements Comparable<PokerPlayer> {
    /** The player's cards. */
    private Hand hand;
    /** The player's cards plus any community cards. */
    private PokerHand pHand;
    /** The player's fold status. */
    private boolean fold;
    /** The player's all-in status. */
    private boolean allIn;
    
    /**
     * Creates a new PokerPlayer.
     * Creates the new player with the specified parameters.
     * 
     * @param nick IRC user's nick.
     * @param hostmask IRC user's hostmask.
     */
    public PokerPlayer(String nick, String hostmask){
        super(nick, hostmask, false);
        statsMap.put("bet", 0);
        hand = new Hand();
        pHand = new PokerHand();
        fold = false;
        allIn = false;
    }

    /**
     * Returns whether the player has cards in his Hand.
     * @return true if player has any cards
     */
    public boolean hasHand(){
        return (hand.getSize() > 0);
    }
    
    /**
     * Returns the player's PokerHand.
     * This includes the player's Hand and any community cards.
     * @return the player's PokerHand
     */
    public PokerHand getPokerHand(){
        return pHand;
    }
    
    /**
     * Returns the player's Hand.
     * @return the player's Hand.
     */
    public Hand getHand(){
        return hand;
    }
    
    /**
     * Clears the cards in the player's Hand and PokerHand.
     */
    public void resetHand(){
        hand.clear();
        pHand.clear();
        pHand.resetValue();
    }
    
    /**
     * Sets the player's fold status to the specified status.
     * @param b the new status
     */
    public void setFold(boolean b){
        fold = b;
    }
    
    /**
     * Whether or not the player has folded.
     * @return true if player has folded
     */
    public boolean hasFolded(){
        return fold;
    }
    
    /**
     * Sets the player's all-in status to the specified value.
     * @param b the new status
     */
    public void setAllIn(boolean b){
        allIn = b;
    }
    
    /**
     * Whether or not the player has gone all in.
     * @return true if the player has gone all in
     */
    public boolean hasAllIn(){
        return allIn;
    }
    
    /**
     * Compares this PokerPlayer to another based on their PokerHand.
     * Returns the same value as a comparison of the players' PokerHands.
     * @param p the PokerPlayer to compare
     * @return the result of the comparison of the players' PokerHands
     */
    @Override
    public int compareTo(PokerPlayer p){
        return this.getPokerHand().compareTo(p.getPokerHand());
    }
}
