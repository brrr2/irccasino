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

package irccasino.blackjack;

import irccasino.cardgame.Player;
import java.util.ArrayList;

/**
 * Extends the Player class for players playing Blackjack.
 * @author Yizhe Shen
 */
class BlackjackPlayer extends Player{
    /** ArrayList containing the player's BlackjackHands. */
    private ArrayList<BlackjackHand> hands;

    /**
     * Creates a new BlackjackPlayer.
     * Creates the new player with the specified parameters.
     * 
     * @param nick IRC user's nick.
     */
    public BlackjackPlayer(String nick){
        super(nick);
        hands = new ArrayList<>();
        put("initialbet", 0);
        put("insurebet", 0);
        put("split", false);
        put("surrender", false);
        put("doubledown", false);
        put("currentindex", 0);
    }

    /* Blackjack-specific card/hand manipulation methods */
    /**
     * Adds a new hand to the ArrayList of BlackjackHands.
     */
    protected void addHand(){
        hands.add(new BlackjackHand());
    }

    /**
     * Gets the BlackjackHand at the specified index.
     * @param num the specified index
     * @return the BlackjackHand at the index
     */
    protected BlackjackHand getHand(int num){
        return hands.get(num);
    }

    /**
     * Gets the player's current BlackjackHand.
     * @return the BlackjackHand at the current index
     */
    protected BlackjackHand getHand(){
        return hands.get(getInteger("currentindex"));
    }

    /**
     * Increments the current index in order to get the next hand.
     * @return the BlackjackHand at the incremented index
     */
    protected BlackjackHand getNextHand(){
        add("currentindex", 1);
        return getHand(getInteger("currentindex"));
    }

    /**
     * Returns the ArrayList hands.
     * @return 
     */
    protected ArrayList<BlackjackHand> getAllHands() {
        return hands;
    }
    
    /**
     * Returns the number BlackjackHands the player has.
     * @return the number of BlackjackHands
     */
    protected int getNumberHands(){
        return hands.size();
    }

    /**
     * Whether or not the player has any BlackjackHands.
     * @return true if the player has any BlackjackHands
     */
    protected boolean hasHands(){
        return hands.size() > 0;
    }

    /**
     * Clears all of the player's hands.
     */
    protected void resetHands(){
        hands.clear();
    }

    /**
     * Splits a BlackjackHand into two BlackjackHands.
     * Creates a new BlackjackHand and gives it the second card of the original.
     * The card is then removed from the original BlackjackHand. The new
     * BlackjackHand is added to the end of the ArrayList of BlackjackHands.
     */
    protected void splitHand(){
        BlackjackHand tHand = new BlackjackHand();
        BlackjackHand cHand = getHand();
        tHand.add(cHand.get(1));
        cHand.remove(1);

        hands.add(getInteger("currentindex") + 1, tHand);
        put("split", true);
    }
}