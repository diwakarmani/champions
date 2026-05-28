package com.propertyapp.config;

import com.propertyapp.entity.property.PropertyAmenity;
import com.propertyapp.entity.property.PropertySubType;
import com.propertyapp.entity.property.PropertyType;
import com.propertyapp.entity.user.Role;
import com.propertyapp.entity.user.User;
import com.propertyapp.repository.property.PropertyAmenityRepository;
import com.propertyapp.repository.property.PropertySubTypeRepository;
import com.propertyapp.repository.property.PropertyTypeRepository;
import com.propertyapp.repository.user.RoleRepository;
import com.propertyapp.repository.user.UserRepository;
import com.propertyapp.util.PasswordUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Initializes default application data on startup
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final PropertyAmenityRepository propertyAmenityRepository;
    private final PropertySubTypeRepository propertySubTypeRepository;

    @Value("${app.admin.email:admin@propertyapp.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.admin.first-name:Admin}")
    private String adminFirstName;

    @Value("${app.admin.last-name:User}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Initializing application data...");

        initializeRoles();
        initializeAdminUser();
        initializePropertyTypes();
        initializePropertySubTypes();
        initializePropertyAmenities();
        log.info("Application data initialization completed successfully");
    }

    private void initializeRoles() {
        log.info("Initializing roles...");

        String[] roleNames = {"BUYER", "SELLER", "REALTOR", "REALTOR_GROUP_ADMIN", "SUPER_ADMIN"};
        String[] roleDescriptions = {
            "Property buyer - can search and inquire about properties",
            "Property seller - can list and manage properties",
            "Real estate agent - can create and manage property listings",
            "Realtor group administrator - manages a group of realtors and approves listings",
            "System administrator - full access to all features"
        };

        for (int i = 0; i < roleNames.length; i++) {
            if (!roleRepository.existsByName(roleNames[i])) {
                Role role = Role.builder()
                        .name(roleNames[i])
                        .description(roleDescriptions[i])
                        .build();
                roleRepository.save(role);
                log.info("Created role: {}", roleNames[i]);
            }
        }
    }

    private void initializeAdminUser() {
        log.info("Checking for admin user...");

        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByName("SUPER_ADMIN")
                    .orElseThrow(() -> new RuntimeException("SUPER_ADMIN role not found"));

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);

            User admin = User.builder()
                    .email(adminEmail)
                    .passwordHash(PasswordUtils.hash(adminPassword))
                    .firstName(adminFirstName)
                    .lastName(adminLastName)
                    .emailVerified(true)
                    .mobileVerified(false)
                    .isLocked(false)
                    .roles(roles)
                    .build();

            userRepository.save(admin);
            log.info("Created admin user: {}", adminEmail);
        } else {
            log.info("Admin user already exists");
        }
    }

    private void initializePropertyTypes() {
        log.info("Initializing property types...");

        String[][] propertyTypesData = {
            {"Apartment", "Multi-story residential building with individual units"},
            {"Villa", "Standalone luxury house with private amenities"},
            {"Independent House", "Standalone residential house"},
            {"Plot", "Land for construction"},
            {"Commercial Space", "Office or retail space"},
            {"Warehouse", "Industrial storage facility"},
            {"Agricultural Land", "Farmland for agricultural purposes"},
            {"Penthouse", "Luxury apartment on top floor"},
            {"Studio Apartment", "Compact single-room living space"}
        };

        for (int i = 0; i < propertyTypesData.length; i++) {
            String name = propertyTypesData[i][0];
            String description = propertyTypesData[i][1];

            if (!propertyTypeRepository.existsByName(name)) {
                PropertyType propertyType = PropertyType.builder()
                        .name(name)
                        .description(description)
                        .displayOrder(i + 1)
                        .isActive(true)
                        .build();
                propertyTypeRepository.save(propertyType);
                log.info("Created property type: {}", name);
            }
        }
    }

    private void initializePropertyAmenities() {
        log.info("Initializing property amenities...");

        String[][] amenitiesData = {
            // Safety & Security
            {"24/7 Security", "fa-shield", "SAFETY"},
            {"CCTV Surveillance", "fa-video", "SAFETY"},
            {"Gated Community", "fa-lock", "SAFETY"},
            {"Fire Safety", "fa-fire-extinguisher", "SAFETY"},

            // Recreation
            {"Swimming Pool", "fa-swimming-pool", "RECREATION"},
            {"Gymnasium", "fa-dumbbell", "RECREATION"},
            {"Children's Play Area", "fa-child", "RECREATION"},
            {"Clubhouse", "fa-home", "RECREATION"},
            {"Garden", "fa-tree", "RECREATION"},
            {"Sports Facility", "fa-basketball-ball", "RECREATION"},

            // Utilities
            {"Power Backup", "fa-bolt", "UTILITIES"},
            {"Water Supply", "fa-tint", "UTILITIES"},
            {"Gas Pipeline", "fa-burn", "UTILITIES"},
            {"Waste Disposal", "fa-trash", "UTILITIES"},
            {"Sewage Treatment", "fa-recycle", "UTILITIES"},

            // Parking & Access
            {"Covered Parking", "fa-car", "PARKING"},
            {"Open Parking", "fa-parking", "PARKING"},
            {"Visitor Parking", "fa-car-side", "PARKING"},
            {"Lift", "fa-elevator", "ACCESS"},
            {"Wheelchair Access", "fa-wheelchair", "ACCESS"},

            // Modern Amenities
            {"Air Conditioning", "fa-snowflake", "MODERN"},
            {"Internet/WiFi", "fa-wifi", "MODERN"},
            {"Intercom", "fa-phone", "MODERN"},
            {"Modular Kitchen", "fa-utensils", "MODERN"},
            {"Piped Gas", "fa-fire", "MODERN"},

            // Services
            {"Maintenance Staff", "fa-tools", "SERVICES"},
            {"Laundry Service", "fa-soap", "SERVICES"},
            {"Shopping Complex", "fa-shopping-cart", "SERVICES"},
            {"School/College", "fa-school", "SERVICES"},
            {"Hospital", "fa-hospital", "SERVICES"}
        };

        for (int i = 0; i < amenitiesData.length; i++) {
            String name = amenitiesData[i][0];

            if (!propertyAmenityRepository.existsByName(name)) {
                PropertyAmenity amenity = PropertyAmenity.builder()
                        .name(name)
                        .iconClass(amenitiesData[i][1])
                        .category(amenitiesData[i][2])
                        .displayOrder(i + 1)
                        .isActive(true)
                        .build();

                propertyAmenityRepository.save(amenity);
                log.info("Created amenity: {}", name);
            }
        }

        log.info("Created {} property amenities", amenitiesData.length);
    }


    private void initializePropertySubTypes() {

        log.info("Initializing property sub types with correct display order...");

        List<PropertyType> propertyTypes = propertyTypeRepository.findAll();

        for (PropertyType type : propertyTypes) {

            String typeName = type.getName();

            String[] subTypes = getSubTypesForType(typeName);

            for (String subTypeName : subTypes) {

                boolean exists = propertySubTypeRepository
                        .existsByNameAndPropertyType(subTypeName, type);

                if (!exists) {

                    Integer maxOrder = propertySubTypeRepository
                            .findMaxDisplayOrderByPropertyType(type);

                    int nextDisplayOrder = (maxOrder == null ? 0 : maxOrder) + 1;

                    PropertySubType subType = PropertySubType.builder()
                            .propertyType(type)
                            .name(subTypeName)
                            .description(subTypeName + " under " + typeName)
                            .displayOrder(nextDisplayOrder)
                            .isActive(true)
                            .build();

                    propertySubTypeRepository.save(subType);

                    log.info("Created subtype: {} -> {} (order {})",
                            typeName, subTypeName, nextDisplayOrder);
                }
            }
        }

        log.info("Property sub types initialized successfully");
    }

    private String[] getSubTypesForType(String typeName) {

        return switch (typeName) {

            case "Apartment" -> new String[]{
                    "1 RK", "1 BHK", "1.5 BHK", "2 BHK", "2.5 BHK",
                    "3 BHK", "3.5 BHK", "4 BHK", "4+ BHK",
                    "Studio Apartment", "Penthouse",
                    "Serviced Apartment", "Loft Apartment",
                    "Duplex Apartment", "Triplex Apartment"
            };

            case "Villa" -> new String[]{
                    "Luxury Villa", "Duplex Villa", "Triplex Villa",
                    "Row Villa", "Independent Villa",
                    "Farm Villa", "Beach Villa", "Hill View Villa"
            };

            case "Independent House" -> new String[]{
                    "1 BHK House", "2 BHK House", "3 BHK House",
                    "4 BHK House", "Bungalow",
                    "Duplex House", "Triplex House",
                    "Row House", "Corner House", "Farm House"
            };

            case "Commercial Space" -> new String[]{
                    "Office Space", "Co-working Space",
                    "Retail Shop", "Showroom",
                    "Business Center", "IT Park Office",
                    "Commercial Floor", "Commercial Building",
                    "Mall Space", "Food Court Space"
            };

            case "Warehouse" -> new String[]{
                    "Cold Storage", "Godown", "Industrial Shed",
                    "Distribution Center", "Logistics Warehouse",
                    "Manufacturing Unit"
            };

            case "Agricultural Land" -> new String[]{
                    "Farmland", "Orchard", "Plantation",
                    "Dairy Farm Land", "Poultry Farm Land",
                    "Greenhouse Farm"
            };

            case "Plot" -> new String[]{
                    "Residential Plot", "Commercial Plot",
                    "Industrial Plot", "Corner Plot",
                    "Gated Community Plot", "Farm Plot",
                    "NA Plot"
            };

            default -> new String[0];
        };
    }

}