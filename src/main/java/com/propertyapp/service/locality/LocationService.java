package com.propertyapp.service.locality;

import com.propertyapp.dto.location.CityResponse;
import com.propertyapp.dto.location.CityResponseDTO;
import com.propertyapp.dto.location.CreateCityRequestDTO;
import com.propertyapp.dto.location.StateResponse;
import com.propertyapp.entity.locality.City;
import com.propertyapp.entity.locality.Country;
import com.propertyapp.entity.locality.Locality;
import com.propertyapp.entity.locality.State;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

public interface LocationService {

    Country createCountry(String name);
    State createState(Long countryId, String name);
    CityResponseDTO createCity(CreateCityRequestDTO request);

    void activateCity(Long cityId);

    List<City> getActiveCities();

    List<Locality> searchLocalities(Long cityId, String keyword);

    void importAllCitiesByCountry(String countryName);

     void importCountry(@RequestParam String name);

     void importStates(@PathVariable Long countryId);

     void importCities(@PathVariable Long stateId);

     List<Country> getCountries() ;

     List<StateResponse> getStates(@PathVariable Long countryId);

     List<CityResponse> getCities(Long stateId);
}