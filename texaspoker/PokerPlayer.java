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

package irccasino.texaspoker;

import irccasino.cardgame.Hand;
import irccasino.cardgame.Player;

/**
 * Extends the Player class for players playing Poker games.
 * @author Yizhe Shen
 */
public class PokerPlayer extends Player implements Comparable<PokerPlayer> {
    /** The player's cards. */
    private Hand hand;
    /** The player's cards plus any community cards. */
    private PokerHand pHand;

    /**
     * Creates a new PokerPlayer.
     * Creates the new player with the specified parameters.
     * 
     * @param nick IRC user's nick.
     */
    public PokerPlayer(String nick){
        super(nick);
        put("bet", 0);
        put("fold", false);
        put("allin", false);
        hand = new Hand();
        pHand = new PokerHand();
    }
    
    @Override
    public Object get(String key) {
        if (key.equalsIgnoreCase("netcash")) {
            return getInteger("cash") + getInteger("bank");
        } else if (key.equalsIgnoreCase("winrate")) {
            return getInteger("winnings") * 1.0 / getInteger("bank");
        }
        return super.get(key);
    }
    
    /**
     * Returns whether the player has cards in his Hand.
     * @return true if player has any cards
     */
    public boolean hasHand(){
        return (hand.size() > 0);
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
     * Compares this PokerPlayer to another based on their PokerHand.
     * Returns the same value as a comparison of the players' PokerHands.
     * @param p the PokerPlayer to compare
     * @return the result of the comparison of the players' PokerHands
     */
    @Override
    public int compareTo(PokerPlayer p){
        return pHand.compareTo(p.pHand);
    }
}