package de.acci.dvmm.config

import com.fasterxml.jackson.databind.ObjectMapper
import de.acci.dvmm.application.vmrequest.ApproveVmRequestHandler
import de.acci.dvmm.application.vmrequest.CancelVmRequestHandler
import de.acci.dvmm.application.vmrequest.CreateVmRequestHandler
import de.acci.dvmm.application.vmrequest.GetMyRequestsHandler
import de.acci.dvmm.application.vmrequest.AdminRequestDetailRepository
import de.acci.dvmm.application.vmrequest.GetAdminRequestDetailHandler
import de.acci.dvmm.application.vmrequest.GetPendingRequestsHandler
import de.acci.dvmm.application.vmrequest.GetRequestDetailHandler
import de.acci.dvmm.application.vmrequest.RejectVmRequestHandler
import de.acci.dvmm.application.vmrequest.TimelineEventProjectionUpdater
import de.acci.dvmm.application.vmrequest.TimelineEventReadRepository
import de.acci.dvmm.application.vmrequest.VmRequestDetailRepository
import de.acci.dvmm.application.vmrequest.VmRequestEventDeserializer
import de.acci.dvmm.application.vmrequest.VmRequestProjectionUpdater
import de.acci.dvmm.application.vmrequest.VmRequestReadRepository
import de.acci.dvmm.application.vmware.CheckVmwareConfigExistsHandler
import de.acci.dvmm.application.vmware.CreateVmwareConfigHandler
import de.acci.dvmm.application.vmware.CredentialEncryptor
import de.acci.dvmm.application.vmware.GetVmwareConfigHandler
import de.acci.dvmm.application.vmware.TestVmwareConnectionHandler
import de.acci.dvmm.application.vmware.UpdateVmwareConfigHandler
import de.acci.dvmm.application.vmware.VmwareConfigurationPort
import de.acci.dvmm.application.vmware.VspherePort
import de.acci.dvmm.infrastructure.eventsourcing.JacksonVmRequestEventDeserializer
import de.acci.dvmm.infrastructure.projection.AdminRequestDetailRepositoryAdapter
import de.acci.dvmm.infrastructure.projection.TimelineEventProjectionUpdaterAdapter
import de.acci.dvmm.infrastructure.projection.TimelineEventReadRepositoryAdapter
import de.acci.dvmm.infrastructure.projection.TimelineEventRepository
import de.acci.dvmm.infrastructure.projection.VmRequestDetailRepositoryAdapter
import de.acci.dvmm.infrastructure.projection.VmRequestProjectionRepository
import de.acci.dvmm.infrastructure.projection.VmRequestProjectionUpdaterAdapter
import de.acci.dvmm.infrastructure.projection.VmRequestReadRepositoryAdapter
import de.acci.eaf.eventsourcing.EventStore
import de.acci.eaf.eventsourcing.EventStoreObjectMapper
import de.acci.eaf.eventsourcing.PostgresEventStore
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * Spring configuration for DVMM application beans.
 *
 * Wires up the application layer handlers with their infrastructure dependencies.
 * Following hexagonal architecture, this is the composition root where
 * ports (interfaces) are connected to adapters (implementations).
 */
@Configuration
public class ApplicationConfig {
    // ==================== Core Infrastructure ====================

    /**
     * jOOQ DSLContext for type-safe SQL operations.
     *
     * Uses PostgreSQL dialect and the Spring-managed DataSource.
     *
     * @param dataSource Spring-managed DataSource (autoconfigured by Spring Boot)
     */
    @Bean
    public fun dslContext(dataSource: DataSource): DSLContext = DSL.using(dataSource, SQLDialect.POSTGRES)

    /**
     * ObjectMapper configured for event store serialization.
     *
     * Handles Kotlin value classes and EAF core types (TenantId, UserId, etc.).
     */
    @Bean
    public fun eventStoreObjectMapper(): ObjectMapper = EventStoreObjectMapper.create()

    /**
     * PostgreSQL-based event store implementation.
     *
     * @param dsl jOOQ DSLContext for database operations
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     */
    @Bean
    public fun eventStore(
        dsl: DSLContext,
        objectMapper: ObjectMapper,
    ): EventStore = PostgresEventStore(dsl, objectMapper)

    // ==================== Projection Infrastructure ====================

    /**
     * Repository for querying VM request projections.
     */
    @Bean
    public fun vmRequestProjectionRepository(dsl: DSLContext): VmRequestProjectionRepository = VmRequestProjectionRepository(dsl)

    /**
     * Adapter for updating VM request projections.
     *
     * Implements the application-layer VmRequestProjectionUpdater port.
     */
    @Bean
    public fun vmRequestProjectionUpdater(projectionRepository: VmRequestProjectionRepository): VmRequestProjectionUpdater =
        VmRequestProjectionUpdaterAdapter(projectionRepository)

    /**
     * Adapter for reading VM request projections.
     *
     * Implements the application-layer VmRequestReadRepository port.
     */
    @Bean
    public fun vmRequestReadRepository(projectionRepository: VmRequestProjectionRepository): VmRequestReadRepository =
        VmRequestReadRepositoryAdapter(projectionRepository)

    /**
     * Repository for timeline event database operations.
     */
    @Bean
    public fun timelineEventRepository(dsl: DSLContext): TimelineEventRepository = TimelineEventRepository(dsl)

    /**
     * Adapter for updating timeline event projections.
     *
     * Implements the application-layer TimelineEventProjectionUpdater port.
     * Used by command handlers to persist timeline events when request status changes.
     */
    @Bean
    public fun timelineEventProjectionUpdater(repository: TimelineEventRepository): TimelineEventProjectionUpdater =
        TimelineEventProjectionUpdaterAdapter(repository)

    // ==================== Event Deserializer ====================

    /**
     * Jackson-based event deserializer for VM request events.
     *
     * Deserializes stored events from JSON to domain event types.
     */
    @Bean
    public fun vmRequestEventDeserializer(objectMapper: ObjectMapper): VmRequestEventDeserializer =
        JacksonVmRequestEventDeserializer(objectMapper)

    // ==================== Command Handlers ====================

    /**
     * Handler for creating VM requests.
     *
     * @param eventStore Event store for persisting domain events
     * @param timelineUpdater Updater for persisting timeline events
     */
    @Bean
    public fun createVmRequestHandler(
        eventStore: EventStore,
        timelineUpdater: TimelineEventProjectionUpdater,
    ): CreateVmRequestHandler = CreateVmRequestHandler(
        eventStore = eventStore,
        timelineUpdater = timelineUpdater
    )

    /**
     * Handler for cancelling VM requests.
     *
     * @param eventStore Event store for loading and persisting events
     * @param eventDeserializer Deserializer for converting stored events to domain events
     * @param projectionUpdater Updater for keeping projections in sync
     * @param timelineUpdater Updater for persisting timeline events
     */
    @Bean
    public fun cancelVmRequestHandler(
        eventStore: EventStore,
        eventDeserializer: VmRequestEventDeserializer,
        projectionUpdater: VmRequestProjectionUpdater,
        timelineUpdater: TimelineEventProjectionUpdater,
    ): CancelVmRequestHandler = CancelVmRequestHandler(
        eventStore = eventStore,
        eventDeserializer = eventDeserializer,
        projectionUpdater = projectionUpdater,
        timelineUpdater = timelineUpdater
    )

    /**
     * Handler for approving VM requests.
     *
     * Story 2.11: Approve/Reject Actions
     *
     * @param eventStore Event store for loading and persisting events
     * @param eventDeserializer Deserializer for converting stored events to domain events
     * @param projectionUpdater Updater for keeping projections in sync
     * @param timelineUpdater Updater for persisting timeline events
     */
    @Bean
    public fun approveVmRequestHandler(
        eventStore: EventStore,
        eventDeserializer: VmRequestEventDeserializer,
        projectionUpdater: VmRequestProjectionUpdater,
        timelineUpdater: TimelineEventProjectionUpdater,
    ): ApproveVmRequestHandler = ApproveVmRequestHandler(
        eventStore = eventStore,
        eventDeserializer = eventDeserializer,
        projectionUpdater = projectionUpdater,
        timelineUpdater = timelineUpdater
    )

    /**
     * Handler for rejecting VM requests.
     *
     * Story 2.11: Approve/Reject Actions
     *
     * @param eventStore Event store for loading and persisting events
     * @param eventDeserializer Deserializer for converting stored events to domain events
     * @param projectionUpdater Updater for keeping projections in sync
     * @param timelineUpdater Updater for persisting timeline events
     */
    @Bean
    public fun rejectVmRequestHandler(
        eventStore: EventStore,
        eventDeserializer: VmRequestEventDeserializer,
        projectionUpdater: VmRequestProjectionUpdater,
        timelineUpdater: TimelineEventProjectionUpdater,
    ): RejectVmRequestHandler = RejectVmRequestHandler(
        eventStore = eventStore,
        eventDeserializer = eventDeserializer,
        projectionUpdater = projectionUpdater,
        timelineUpdater = timelineUpdater
    )

    // ==================== Query Handlers ====================

    /**
     * Handler for retrieving the current user's VM requests.
     *
     * @param readRepository Repository for querying VM request projections
     */
    @Bean
    public fun getMyRequestsHandler(readRepository: VmRequestReadRepository): GetMyRequestsHandler = GetMyRequestsHandler(readRepository)

    /**
     * Handler for retrieving pending VM requests for admin approval.
     *
     * Story 2.9: Admin Approval Queue
     *
     * @param readRepository Repository for querying VM request projections
     */
    @Bean
    public fun getPendingRequestsHandler(readRepository: VmRequestReadRepository): GetPendingRequestsHandler =
        GetPendingRequestsHandler(readRepository)

    /**
     * Adapter for reading detailed VM request information.
     *
     * Implements the application-layer VmRequestDetailRepository port.
     */
    @Bean
    public fun vmRequestDetailRepository(dsl: DSLContext): VmRequestDetailRepository = VmRequestDetailRepositoryAdapter(dsl)

    /**
     * Adapter for reading timeline events.
     *
     * Implements the application-layer TimelineEventReadRepository port.
     */
    @Bean
    public fun timelineEventReadRepository(dsl: DSLContext): TimelineEventReadRepository = TimelineEventReadRepositoryAdapter(dsl)

    /**
     * Handler for retrieving detailed VM request information with timeline.
     *
     * @param requestRepository Repository for querying VM request details
     * @param timelineRepository Repository for querying timeline events
     */
    @Bean
    public fun getRequestDetailHandler(
        requestRepository: VmRequestDetailRepository,
        timelineRepository: TimelineEventReadRepository,
    ): GetRequestDetailHandler = GetRequestDetailHandler(
        requestRepository = requestRepository,
        timelineRepository = timelineRepository
    )

    // ==================== Admin Query Handlers (Story 2.10) ====================

    /**
     * Adapter for reading admin-specific VM request details.
     *
     * Story 2.10: Request Detail View (Admin)
     *
     * Provides access to requester info (email, role) and requester history
     * that are only visible to admins.
     */
    @Bean
    public fun adminRequestDetailRepository(dsl: DSLContext): AdminRequestDetailRepository =
        AdminRequestDetailRepositoryAdapter(dsl)

    /**
     * Handler for retrieving admin-specific VM request details.
     *
     * Story 2.10: Request Detail View (Admin)
     *
     * Returns full request details including:
     * - Requester info (name, email, role)
     * - Request specs and justification
     * - Timeline events
     * - Requester history (up to 5 recent requests)
     *
     * @param adminRequestRepository Repository for admin-specific request queries
     * @param timelineRepository Repository for querying timeline events
     */
    @Bean
    public fun getAdminRequestDetailHandler(
        adminRequestRepository: AdminRequestDetailRepository,
        timelineRepository: TimelineEventReadRepository,
    ): GetAdminRequestDetailHandler = GetAdminRequestDetailHandler(
        requestRepository = adminRequestRepository,
        timelineRepository = timelineRepository
    )

    // ==================== VMware Configuration Handlers (Story 3.1) ====================

    /**
     * Handler for creating VMware vCenter configuration.
     *
     * Story 3.1: VMware Connection Configuration (AC-3.1.1, AC-3.1.4)
     *
     * @param configurationPort Port for configuration persistence
     * @param credentialEncryptor Encryptor for securing credentials
     */
    @Bean
    public fun createVmwareConfigHandler(
        configurationPort: VmwareConfigurationPort,
        credentialEncryptor: CredentialEncryptor,
    ): CreateVmwareConfigHandler = CreateVmwareConfigHandler(
        configurationPort = configurationPort,
        credentialEncryptor = credentialEncryptor
    )

    /**
     * Handler for updating VMware vCenter configuration.
     *
     * Story 3.1: VMware Connection Configuration (AC-3.1.1, AC-3.1.4)
     *
     * @param configurationPort Port for configuration persistence
     * @param credentialEncryptor Encryptor for securing credentials
     */
    @Bean
    public fun updateVmwareConfigHandler(
        configurationPort: VmwareConfigurationPort,
        credentialEncryptor: CredentialEncryptor,
    ): UpdateVmwareConfigHandler = UpdateVmwareConfigHandler(
        configurationPort = configurationPort,
        credentialEncryptor = credentialEncryptor
    )

    /**
     * Handler for retrieving VMware configuration.
     *
     * Story 3.1: VMware Connection Configuration (AC-3.1.1)
     *
     * @param configurationPort Port for configuration persistence
     */
    @Bean
    public fun getVmwareConfigHandler(
        configurationPort: VmwareConfigurationPort,
    ): GetVmwareConfigHandler = GetVmwareConfigHandler(
        configurationPort = configurationPort
    )

    /**
     * Handler for testing VMware vCenter connection.
     *
     * Story 3.1: VMware Connection Configuration (AC-3.1.2, AC-3.1.3)
     *
     * @param vspherePort Port for vSphere API operations
     * @param configurationPort Port for configuration persistence
     */
    @Bean
    public fun testVmwareConnectionHandler(
        vspherePort: VspherePort,
        configurationPort: VmwareConfigurationPort,
    ): TestVmwareConnectionHandler = TestVmwareConnectionHandler(
        vspherePort = vspherePort,
        configurationPort = configurationPort
    )

    /**
     * Handler for checking if VMware configuration exists.
     *
     * Story 3.1: VMware Connection Configuration (AC-3.1.5)
     *
     * Lightweight query for "VMware not configured" warning.
     *
     * @param configurationPort Port for configuration persistence
     */
    @Bean
    public fun checkVmwareConfigExistsHandler(
        configurationPort: VmwareConfigurationPort,
    ): CheckVmwareConfigExistsHandler = CheckVmwareConfigExistsHandler(
        configurationPort = configurationPort
    )
}
