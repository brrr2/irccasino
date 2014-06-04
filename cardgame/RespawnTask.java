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

package irccasino.cardgame;

import java.util.TimerTask;

/**
 * Respawn task for giving loans after bankruptcies.
 * @author Yizhe Shen
 */
public class RespawnTask extends TimerTask {
    private final Player player;
    private final CardGame game;
    
    public RespawnTask(Player p, CardGame g) {
        player = p;
        game = g;
    }
    
    @Override
    public void run() {
        player.set("cash", game.get("cash"));
        player.add("bank", -game.get("cash"));
        player.set("transaction", game.get("cash"));
        game.savePlayerData(player);
        game.saveDBPlayerData(player);
        game.saveDBPlayerBanking(player);
        game.removeBlacklisted(player);
        game.getRespawnTasks().remove(this);
        game.showMsg(game.getMsg("respawn"), player.getNickStr(), game.get("cash"));
    }
}