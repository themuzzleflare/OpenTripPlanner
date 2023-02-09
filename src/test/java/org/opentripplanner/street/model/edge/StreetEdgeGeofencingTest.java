package org.opentripplanner.street.model.edge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.opentripplanner.street.model._data.StreetModelForTest.intersectionVertex;
import static org.opentripplanner.street.model._data.StreetModelForTest.streetEdge;
import static org.opentripplanner.street.search.TraverseMode.BICYCLE;
import static org.opentripplanner.street.search.TraverseMode.WALK;
import static org.opentripplanner.street.search.state.VehicleRentalState.HAVE_RENTED;
import static org.opentripplanner.street.search.state.VehicleRentalState.RENTING_FLOATING;

import java.util.Set;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentripplanner.routing.api.request.StreetMode;
import org.opentripplanner.routing.vehicle_rental.GeofencingZone;
import org.opentripplanner.routing.vehicle_rental.RentalVehicleType;
import org.opentripplanner.street.model.vertex.RentalRestrictionExtension;
import org.opentripplanner.street.model.vertex.RentalRestrictionExtension.BusinessAreaBorder;
import org.opentripplanner.street.model.vertex.StreetVertex;
import org.opentripplanner.street.model.vertex.Vertex;
import org.opentripplanner.street.search.TraverseMode;
import org.opentripplanner.street.search.request.StreetSearchRequest;
import org.opentripplanner.street.search.state.State;
import org.opentripplanner.street.search.state.StateEditor;
import org.opentripplanner.transit.model.framework.FeedScopedId;

class StreetEdgeGeofencingTest {

  static String network = "tier-oslo";
  static RentalRestrictionExtension NO_DROP_OFF = new RentalRestrictionExtension.GeofencingZoneExtension(
    new GeofencingZone(new FeedScopedId(network, "a-park"), null, true, false)
  );
  static RentalRestrictionExtension NO_TRAVERSAL = new RentalRestrictionExtension.GeofencingZoneExtension(
    new GeofencingZone(new FeedScopedId(network, "a-park"), null, false, true)
  );
  StreetVertex V1 = intersectionVertex("V1", 0, 0);
  StreetVertex V2 = intersectionVertex("V2", 1, 1);
  StreetVertex V3 = intersectionVertex("V3", 2, 2);
  StreetVertex V4 = intersectionVertex("V4", 3, 3);

  @Nested
  class Forward {

    @Test
    public void finishInEdgeWithoutRestrictions() {
      var edge = streetEdge(V1, V2);
      State result = traverseFromV1(edge);
      assertTrue(result.isFinal());
    }

    @Test
    public void leaveBusinessAreaOnFoot() {
      var edge1 = streetEdge(V1, V2);
      var ext = new BusinessAreaBorder(network);
      V2.addRentalRestriction(ext);

      State result = traverseFromV1(edge1);
      assertEquals(HAVE_RENTED, result.getVehicleRentalState());
      assertEquals(TraverseMode.WALK, result.getBackMode());
      assertNull(result.getNextResult());
    }

    @Test
    public void dontEnterGeofencingZoneOnFoot() {
      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(
        new RentalRestrictionExtension.GeofencingZoneExtension(
          new GeofencingZone(new FeedScopedId(network, "a-park"), null, true, true)
        )
      );
      State result = traverseFromV1(edge);
      assertEquals(WALK, result.getBackMode());
      assertEquals(HAVE_RENTED, result.getVehicleRentalState());
    }

    @Test
    public void forkStateWhenEnteringNoDropOffZone() {
      var edge1 = streetEdge(V4, V1);
      var edge2 = streetEdge(V2, V3);
      var restrictedEdge = streetEdge(V1, V2);

      var req = StreetSearchRequest.of().withMode(StreetMode.SCOOTER_RENTAL).build();
      var editor = new StateEditor(edge1.getFromVertex(), req);
      editor.beginFloatingVehicleRenting(RentalVehicleType.FormFactor.SCOOTER, network, false);
      restrictedEdge.addRentalRestriction(
        new RentalRestrictionExtension.GeofencingZoneExtension(
          new GeofencingZone(new FeedScopedId(network, "a-park"), null, true, false)
        )
      );

      var continueOnFoot = edge1.traverse(editor.makeState());

      assertEquals(HAVE_RENTED, continueOnFoot.getVehicleRentalState());
      assertEquals(WALK, continueOnFoot.getBackMode());

      var continueRenting = continueOnFoot.getNextResult();
      assertEquals(RENTING_FLOATING, continueRenting.getVehicleRentalState());
      assertEquals(BICYCLE, continueRenting.getBackMode());
      assertTrue(continueRenting.isInsideNoRentalDropOffArea());

      var insideZone = restrictedEdge.traverse(continueRenting);

      var leftNoDropOff = edge2.traverse(insideZone);
      assertFalse(leftNoDropOff.isInsideNoRentalDropOffArea());
      assertEquals(RENTING_FLOATING, continueRenting.getVehicleRentalState());
    }

    @Test
    public void forwardDontFinishInNoDropOffZone() {
      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(NO_DROP_OFF);
      edge.addRentalRestriction(NO_DROP_OFF);
      State result = traverseFromV1(edge);
      assertFalse(result.isFinal());
    }
  }

  @Nested
  class Backwards {

    @Test
    public void backwardsRejectWhenEnteringNoTraversalZone() {
      var restrictedEdge = streetEdge(V1, V2);
      V2.addRentalRestriction(NO_DROP_OFF);

      var req = StreetSearchRequest
        .of()
        .withMode(StreetMode.SCOOTER_RENTAL)
        .withArriveBy(true)
        .build();

      var editor = new StateEditor(restrictedEdge.getToVertex(), req);
      editor.dropFloatingVehicle(RentalVehicleType.FormFactor.SCOOTER, network, true);

      var result = restrictedEdge.traverse(editor.makeState());

      assertNull(result);
    }

    @Test
    public void backwardDontFinishInNoDropOffZone() {
      var edge = streetEdge(V1, V2);
      edge.addRentalRestriction(NO_DROP_OFF);
      var state = initialState(V2, network, true);
      var state2 = edge.traverse(state);
      assertFalse(state2.isFinal());
    }

    @Test
    public void backwardsDontEnterNoTraversalZone() {
      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(NO_TRAVERSAL);
      var intialState = initialState(V2, network, true);
      var result = edge.traverse(intialState);
      assertNull(result);
    }

    @Test
    public void pickupFloatingVehicleWhenLeavingAZone() {
      var req = StreetSearchRequest
        .of()
        .withMode(StreetMode.SCOOTER_RENTAL)
        .withArriveBy(true)
        .build();

      // this is the state that starts inside a restricted zone (no drop off, no traversal or outside business area)
      // and is walking towards finding a rental vehicle
      var haveRentedState = State
        .getInitialStates(Set.of(V2), req)
        .stream()
        .filter(s -> s.getVehicleRentalState() == HAVE_RENTED)
        .findAny()
        .get();

      var edge = streetEdge(V1, V2);
      V2.addRentalRestriction(NO_TRAVERSAL);
      var result = edge.traverse(haveRentedState);

      // we want to pick up a vehicle
      final State rentalState = result.getNextResult();
      assertEquals(RENTING_FLOATING, rentalState.getVehicleRentalState());
      assertEquals(BICYCLE, rentalState.getNonTransitMode());

      // but also keep on walking in case we don't find an edge where to leave the vehicle
      assertEquals(HAVE_RENTED, result.getVehicleRentalState());
      assertEquals(WALK, result.getNonTransitMode());
    }
  }

  @Test
  public void addTwoExtensions() {
    var edge = streetEdge(V1, V2);
    edge.addRentalRestriction(new BusinessAreaBorder("a"));
    edge.addRentalRestriction(new BusinessAreaBorder("b"));

    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("a")));
    assertTrue(edge.fromv.rentalTraversalBanned(forwardState("b")));
  }

  @Test
  public void removeExtensions() {
    var edge = streetEdge(V1, V2);
    var a = new BusinessAreaBorder("a");
    var b = new BusinessAreaBorder("b");
    var c = new BusinessAreaBorder("c");

    edge.addRentalRestriction(a);

    assertTrue(edge.fromv.rentalRestrictions().traversalBanned(forwardState("a")));

    edge.addRentalRestriction(b);
    edge.addRentalRestriction(c);

    edge.removeRentalExtension(a);

    assertTrue(edge.fromv.rentalRestrictions().traversalBanned(forwardState("b")));
    assertTrue(edge.fromv.rentalRestrictions().traversalBanned(forwardState("c")));

    edge.removeRentalExtension(b);

    assertTrue(edge.fromv.rentalRestrictions().traversalBanned(forwardState("c")));
  }

  @Test
  public void checkNetwork() {
    var edge = streetEdge(V1, V2);
    edge.addRentalRestriction(new BusinessAreaBorder("a"));

    var state = traverseFromV1(edge);

    assertEquals(RENTING_FLOATING, state.getVehicleRentalState());
    assertNull(state.getNextResult());
  }

  private State traverseFromV1(StreetEdge edge) {
    var state = initialState(V1, network, false);
    return edge.traverse(state);
  }

  @Nonnull
  private State forwardState(String network) {
    return initialState(V1, network, false);
  }

  @Nonnull
  private State initialState(Vertex startVertex, String network, boolean arriveBy) {
    var req = StreetSearchRequest
      .of()
      .withMode(StreetMode.SCOOTER_RENTAL)
      .withArriveBy(arriveBy)
      .build();
    var editor = new StateEditor(startVertex, req);
    editor.beginFloatingVehicleRenting(RentalVehicleType.FormFactor.SCOOTER, network, false);
    return editor.makeState();
  }
}
