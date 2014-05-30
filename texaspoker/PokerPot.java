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

import java.util.ArrayList;
import java.util.HashMap;

/**
 * A pot class to handle bets and payouts in Texas Hold'em Poker.
 * @author Yizhe Shen
 */
public class PokerPot {
    private HashMap<PokerPlayer,Boolean> eligibles;
    private HashMap<PokerPlayer,Integer> donations;

    /**
     * Initializes eligibles and donations ArrayLists.
     */
    public PokerPot(){
        eligibles = new HashMap<>();
        donations = new HashMap<>();
    }

    /**
     * Adds a player's bet to the pot.
     * @param p
     * @param amount 
     */
    public void contribute(PokerPlayer p, int amount) {
        if (eligibles.containsKey(p)) {
            donations.put(p, donations.get(p) + amount);
        } else {
            eligibles.put(p, Boolean.TRUE);
            donations.put(p, amount);
        }
    }
    
    /**
     * Removes a player's eligibility to win the pot.
     * @param p 
     */
    public void disqualify(PokerPlayer p) {
        eligibles.put(p, Boolean.FALSE);
    }
    
    /**
     * Determines if a player is eligible to win the pot.
     * @param p
     * @return 
     */
    public boolean isEligible(PokerPlayer p) {
        return eligibles.containsKey(p) && eligibles.get(p);
    }
    
    /**
     * Determines the total amount contributed to the pot.
     * @return 
     */
    public int getTotal(){
        int total = 0;
        for (Integer amount : donations.values()) {
            total += amount;
        }
        return total;
    }
    
    /**
     * Returns an ArrayList of all players eligible to win this pot.
     * @return 
     */
    public ArrayList<PokerPlayer> getEligibles(){
        ArrayList<PokerPlayer> list = new ArrayList<>();
        for (PokerPlayer p : eligibles.keySet()) {
            if (eligibles.get(p)) {
                list.add(p);
            }
        }
        return list;
    }
    
    /**
     * Returns an ArrayList of all players who have contributed to this pot.
     * @return 
     */
    public ArrayList<PokerPlayer> getDonors() {
        return new ArrayList<>(donations.keySet());
    }
    
    /**
     * Determines the number of players eligible to win this pot.
     * @return 
     */
    public int getNumEligible(){
        int total = 0;
        for (Boolean b : eligibles.values()) {
            if (b) {
                total++;
            }
        }
        return total;
    }
    
    /**
     * Determines the number of contributors to this pot.
     * @return 
     */
    public int getNumDonors() {
        return donations.size();
    }
}