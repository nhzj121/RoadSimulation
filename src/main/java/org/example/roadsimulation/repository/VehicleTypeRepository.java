package org.example.roadsimulation.repository;

import org.example.roadsimulation.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleTypeRepository extends JpaRepository<VehicleType, Long> {
    List<VehicleType> findByIsAvailableTrue();
    List<VehicleType> findByCategoryAndIsAvailableTrue(String category);

    @Query("SELECT v FROM VehicleType v WHERE " +
            "v.maxLoadWeight >= :minWeight AND " +
            "v.maxLoadVolume >= :minVolume AND " +
            "v.isAvailable = true")
    List<VehicleType> findByLoadCapacity(@Param("minWeight") Double minWeight,
                                         @Param("minVolume") Double minVolume);

    @Query("SELECT v FROM VehicleType v WHERE " +
            "v.hasTempControl = true AND v.isAvailable = true")
    List<VehicleType> findTempControlVehicles();

    List<VehicleType> findByHasTempControlAndIsAvailableTrue(Boolean hasTempControl);
    Optional<VehicleType> findByCode(String code);
}