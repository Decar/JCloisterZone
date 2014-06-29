package com.jcloisterzone.game.phase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.jcloisterzone.LittleBuilding;
import com.jcloisterzone.action.MeepleAction;
import com.jcloisterzone.action.PlayerAction;
import com.jcloisterzone.action.TakePrisonerAction;
import com.jcloisterzone.board.Location;
import com.jcloisterzone.board.Position;
import com.jcloisterzone.board.TileTrigger;
import com.jcloisterzone.board.pointer.FeaturePointer;
import com.jcloisterzone.board.pointer.MeeplePointer;
import com.jcloisterzone.event.FlierRollEvent;
import com.jcloisterzone.event.NeutralFigureMoveEvent;
import com.jcloisterzone.event.SelectActionEvent;
import com.jcloisterzone.event.TowerIncreasedEvent;
import com.jcloisterzone.feature.City;
import com.jcloisterzone.feature.Tower;
import com.jcloisterzone.figure.Follower;
import com.jcloisterzone.figure.Meeple;
import com.jcloisterzone.figure.SmallFollower;
import com.jcloisterzone.figure.predicate.MeeplePredicates;
import com.jcloisterzone.game.Game;
import com.jcloisterzone.game.capability.BridgeCapability;
import com.jcloisterzone.game.capability.FairyCapability;
import com.jcloisterzone.game.capability.FlierCapability;
import com.jcloisterzone.game.capability.LittleBuildingsCapability;
import com.jcloisterzone.game.capability.PortalCapability;
import com.jcloisterzone.game.capability.TowerCapability;
import com.jcloisterzone.game.capability.TunnelCapability;


public class ActionPhase extends Phase {

    private final TowerCapability towerCap;
    private final FlierCapability flierCap;
    private final PortalCapability portalCap;

    public ActionPhase(Game game) {
        super(game);
        towerCap = game.getCapability(TowerCapability.class);
        flierCap = game.getCapability(FlierCapability.class);
        portalCap = game.getCapability(PortalCapability.class);
    }

    @Override
    public void enter() {
        List<PlayerAction<?>> actions = new ArrayList<>();

        Set<FeaturePointer> followerLocations = game.prepareFollowerLocations();
        if (getActivePlayer().hasFollower(SmallFollower.class)  && !followerLocations.isEmpty()) {
            actions.add(new MeepleAction(SmallFollower.class).addAll(followerLocations));
        }
        game.prepareActions(actions, ImmutableSet.copyOf(followerLocations));
        if (isAutoTurnEnd(actions)) {
            next();
        } else {
            game.post(new SelectActionEvent(getActivePlayer(), actions, true));
        }
    }

    @Override
    public void notifyRansomPaid() {
        enter(); //recompute available actions
    }

    private boolean isAutoTurnEnd(List<? extends PlayerAction<?>> actions) {
        if (!actions.isEmpty()) return false;
        if (towerCap != null && !towerCap.isRansomPaidThisTurn() && towerCap.hasImprisonedFollower(getActivePlayer())) {
            //player can return figure immediately
            return false;
        }
        return true;
    }

    @Override
    public void pass() {
        if (getDefaultNext() instanceof PhantomPhase) {
            //skip PhantomPhase if user pass turn
            getDefaultNext().next();
        } else {
            next();
        }
    }

    private int doPlaceTowerPiece(Position p) {
        Tower tower = getBoard().get(p).getTower();
        if (tower  == null) {
            throw new IllegalArgumentException("No tower on tile.");
        }
        if (tower.getMeeple() != null) {
            throw new IllegalArgumentException("The tower is sealed");
        }
        towerCap.decreaseTowerPieces(getActivePlayer());
        return tower.increaseHeight();
    }

    public TakePrisonerAction prepareCapture(Position p, int range) {
        //TODO custom rule - opponent only
        TakePrisonerAction captureAction = new TakePrisonerAction();
        for (Meeple pf : game.getDeployedMeeples()) {
            if (!(pf instanceof Follower)) continue;
            Position pos = pf.getPosition();
            if (pos.x != p.x && pos.y != p.y) continue; //check if is in same row or column
            if (pos.squareDistance(p) > range) continue;
            captureAction.add(new MeeplePointer(pf));
        }
        return captureAction;
    }

    @Override
    public void placeTowerPiece(Position p) {
        int captureRange = doPlaceTowerPiece(p);
        game.post(new TowerIncreasedEvent(getActivePlayer(), p, captureRange));
        TakePrisonerAction captureAction = prepareCapture(p, captureRange);
        if (captureAction.isEmpty()) {
            next();
            return;
        }
        next(TowerCapturePhase.class);
        game.post(new SelectActionEvent(getActivePlayer(), captureAction, false));
    }

    @Override
    public void placeLittleBuilding(LittleBuilding lbType) {
        LittleBuildingsCapability lbCap = game.getCapability(LittleBuildingsCapability.class);
        lbCap.placeLittleBuilding(getActivePlayer(), lbType);
        next();
    }

    @Override
    public void moveFairy(Position p) {
        if (!Iterables.any(getActivePlayer().getFollowers(), MeeplePredicates.at(p))) {
            throw new IllegalArgumentException("The tile has deployed not own follower.");
        }

        FairyCapability cap = game.getCapability(FairyCapability.class);
        Position fromPosition = cap.getFairyPosition();
        cap.setFairyPosition(p);
        game.post(new NeutralFigureMoveEvent(NeutralFigureMoveEvent.FAIRY, getActivePlayer(), fromPosition, p));
        next();
    }

    private boolean isFestivalUndeploy(Meeple m) {
        return getTile().hasTrigger(TileTrigger.FESTIVAL) && m.getPlayer() == getActivePlayer();
    }

    private boolean isPrincessUndeploy(Meeple m) {
        //TODO proper validation
        return m.getFeature() instanceof City;
    }

    @Override
    public void undeployMeeple(Position p, Location loc, Class<? extends Meeple> meepleType, Integer meepleOwner) {
        Meeple m = game.getMeeple(p, loc, meepleType, game.getPlayer(meepleOwner));
        if (isFestivalUndeploy(m) || isPrincessUndeploy(m)) {
            m.undeploy();
            next();
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void placeTunnelPiece(Position p, Location loc, boolean isB) {
        game.getCapability(TunnelCapability.class).placeTunnelPiece(p, loc, isB);
        next(ActionPhase.class);
    }


    @Override
    public void deployMeeple(Position p, Location loc, Class<? extends Meeple> meepleType) {
        Meeple m = getActivePlayer().getMeepleFromSupply(meepleType);
        m.deployUnoccupied(getBoard().get(p), loc);
        if (!p.equals(getTile().getPosition()) && portalCap != null) {
            //magic gate usage
            portalCap.setPortalUsed(true);
        }
        next();
    }

    @Override
    public void deployBridge(Position pos, Location loc) {
        BridgeCapability bridgeCap = game.getCapability(BridgeCapability.class);
        bridgeCap.decreaseBridges(getActivePlayer());
        bridgeCap.deployBridge(pos, loc);
        next(ActionPhase.class);
    }

    @Override
    public void setFlierDistance(Class<? extends Meeple> meepleType, int distance) {
        flierCap.setFlierDistance(meepleType, distance);
        game.post(new FlierRollEvent(getActivePlayer(), getTile().getPosition(), distance));
        next(FlierActionPhase.class);
    }

}
