package org.opentripplanner.ext.vehicleparking.bikely;

import static org.opentripplanner.routing.vehicle_parking.VehicleParkingState.OPERATIONAL;

import com.fasterxml.jackson.databind.JsonNode;
import org.opentripplanner.routing.vehicle_parking.VehicleParking;
import org.opentripplanner.routing.vehicle_parking.VehicleParkingSpaces;
import org.opentripplanner.routing.vehicle_parking.VehicleParkingState;
import org.opentripplanner.transit.model.basic.NonLocalizedString;
import org.opentripplanner.transit.model.basic.WgsCoordinate;
import org.opentripplanner.transit.model.framework.FeedScopedId;
import org.opentripplanner.updater.GenericJsonDataSource;

/**
 * Vehicle parking updater class for the Norwegian bike box provider Bikely:
 * https://www.safebikely.com/
 */
class BikelyUpdater extends GenericJsonDataSource<VehicleParking> {

  private static final String JSON_PARSE_PATH = "result";

  private final String feedId;

  public BikelyUpdater(BikelyUpdaterParameters parameters) {
    super(parameters.url(), JSON_PARSE_PATH, parameters.httpHeaders());
    this.feedId = parameters.feedId();
  }

  @Override
  protected VehicleParking parseElement(JsonNode jsonNode) {
    var vehicleParkId = new FeedScopedId(feedId, jsonNode.get("id").asText());

    var address = jsonNode.get("address");
    var workingHours = jsonNode.get("workingHours");

    var lat = address.get("latitude").asDouble();
    var lng = address.get("longitude").asDouble();
    var coord = new WgsCoordinate(lat, lng);

    var name = new NonLocalizedString(jsonNode.path("name").asText());

    var freeSpots = jsonNode.get("availableParkingSpots").asInt();
    var isUnderMaintenance = workingHours.get("isUnderMaintenance").asBoolean();

    VehicleParking.VehicleParkingEntranceCreator entrance = builder ->
      builder
        .entranceId(new FeedScopedId(feedId, vehicleParkId.getId() + "/entrance"))
        .name(name)
        .coordinate(coord)
        .walkAccessible(true)
        .carAccessible(false);

    return VehicleParking
      .builder()
      .id(vehicleParkId)
      .name(name)
      .bicyclePlaces(true)
      .availability(VehicleParkingSpaces.builder().bicycleSpaces(freeSpots).build())
      .state(toState(isUnderMaintenance))
      .coordinate(coord)
      .entrance(entrance)
      .build();
  }

  private VehicleParkingState toState(boolean isUnderMaintenance) {
    if (isUnderMaintenance) return VehicleParkingState.TEMPORARILY_CLOSED; else return OPERATIONAL;
  }
}
