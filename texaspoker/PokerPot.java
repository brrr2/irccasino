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

/**
 * A pot class to handle bets and payouts in Texas Hold'em Poker.
 * @author Yizhe Shen
 */
class PokerPot {
    private ArrayList<PokerPlayer> players;
    private ArrayList<PokerPlayer> donors;
    private int total;

    public PokerPot(){
        total = 0;
        players = new ArrayList<PokerPlayer>();
        donors = new ArrayList<PokerPlayer>();
    }

    protected int getTotal(){
        return total;
    }
    protected void add(int amount){
        total += amount;
    }
    protected void addPlayer(PokerPlayer p){
        players.add(p);
    }
    protected void removePlayer(PokerPlayer p){
        players.remove(p);
    }
    protected void addDonor(PokerPlayer p) {
        donors.add(p);
    }
    protected void removeDonor(PokerPlayer p) {
        donors.remove(p);
    }
    protected PokerPlayer getPlayer(int c){
        return players.get(c);
    }
    protected ArrayList<PokerPlayer> getPlayers(){
        return players;
    }
    protected PokerPlayer getDonor(int c) {
        return donors.get(c);
    }
    protected ArrayList<PokerPlayer> getDonors() {
        return donors;
    }
    protected boolean hasPlayer(PokerPlayer p){
        return players.contains(p);
    }
    protected boolean hasDonor(PokerPlayer p) {
        return donors.contains(p);
    }
    protected int getNumPlayers(){
        return players.size();
    }
    protected int getNumDonors() {
        return donors.size();
    }
}