package com.jcloisterzone.action;

import com.jcloisterzone.board.Position;
import com.jcloisterzone.rmi.RmiProxy;
import com.jcloisterzone.ui.grid.ActionLayer;
import com.jcloisterzone.ui.grid.layer.TileActionLayer;


public class TowerPieceAction extends SelectTileAction {


    public TowerPieceAction() {
        super("towerpiece");
    }

    @Override
    public void perform(RmiProxy server, Position p) {
        server.placeTowerPiece(p);
    }

    @Override
    protected int getSortOrder() {
        return 20;
    }

    @Override
    protected Class<? extends ActionLayer<?>> getActionLayerType() {
        return TileActionLayer.class;
    }


    @Override
    public String toString() {
        return "place tower piece";
    }

}
