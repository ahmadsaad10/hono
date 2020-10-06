/*******************************************************************************
 * Copyright (c) 2020 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.hono.deviceregistry.mongodb.model;

import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.hono.client.ClientErrorException;
import org.eclipse.hono.deviceregistry.mongodb.utils.MongoDbDeviceRegistryUtils;
import org.eclipse.hono.service.management.credentials.CommonCredential;
import org.eclipse.hono.util.RegistryManagementConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A DTO (Data Transfer Object) class to store credentials information in mongodb.
 */
public final class CredentialsDto extends BaseDto {

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_TENANT_ID, required = true)
    private String tenantId;

    @JsonProperty(value = RegistryManagementConstants.FIELD_PAYLOAD_DEVICE_ID, required = true)
    private String deviceId;

    @JsonProperty(value = MongoDbDeviceRegistryUtils.FIELD_CREDENTIALS, required = true)
    private List<CommonCredential> credentials;
    private boolean requiresMerging;

    /**
     * Default constructor for serialisation/deserialization.
     */
    public CredentialsDto() {
    }

    /**
     * Creates a new instance for a list of credentials.
     * <p>
     * This constructor also makes sure that the identifiers of the credentials'
     * secrets are unique within their credentials object.
     * <p>
     * These properties are supposed to be asserted before persisting this DTO.
     *
     * @param tenantId The tenant identifier.
     * @param deviceId The device identifier.
     * @param credentials The list of credentials.
     * @param version The version of the credentials to be sent as request header.
     * @throws NullPointerException if any of the parameters except credentials is {@code null}
     * @throws org.eclipse.hono.client.ClientErrorException if any of the checks fail.
     */
    public CredentialsDto(
            final String tenantId,
            final String deviceId,
            final List<CommonCredential> credentials,
            final String version) {

        setTenantId(tenantId);
        setDeviceId(deviceId);

        //Validate the given credentials, secrets and generate secret ids if not available.
        Optional.ofNullable(credentials)
                .ifPresent(creds -> {
                    assertSecretIdUniqueness(creds);
                });
        setCredentials(credentials);

        setVersion(version);
        setUpdatedOn(Instant.now());
    }

    @JsonIgnore
    private void assertSecretIdUniqueness(final List<? extends CommonCredential> credentials) {

        credentials.stream()
            .map(CommonCredential::getSecrets)
            .forEach(secrets -> {
                final Set<String> secretIds = new HashSet<>();
                final AtomicInteger count = new AtomicInteger(0);
                secrets.stream()
                    .forEach(secret -> {
                        if (secret.getId() != null) {
                            requiresMerging = true;
                            secretIds.add(secret.getId());
                            count.incrementAndGet();
                        }
                    });
                if (secretIds.size() < count.get()) {
                    throw new ClientErrorException(HttpURLConnection.HTTP_BAD_REQUEST,
                            "secret IDs must be unique within each credentials object");
                }
            });
    }

    /**
     * Gets the identifier of the tenant.
     *
     * @return The identifier of the tenant.
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Sets the identifier of the tenant.
     *
     * @param tenantId The tenant's identifier.
     * @throws NullPointerException if the tenantId is {@code null}.
     */
    public void setTenantId(final String tenantId) {
        this.tenantId = Objects.requireNonNull(tenantId);
    }

    /**
     * Gets the identifier of the device.
     *
     * @return The identifier of the device.
     */
    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Sets the identifier of the device.
     *
     * @param deviceId The identifier of the device.
     * @throws NullPointerException if the deviceId is {@code null}.
     */
    public void setDeviceId(final String deviceId) {
        this.deviceId = Objects.requireNonNull(deviceId);
    }

    /**
     * Gets the list of credentials.
     *
     * @return The list of credentials.
     */
    public List<CommonCredential> getCredentials() {
        return credentials;
    }

    /**
     * Sets the list of credentials.
     *
     * @param credentials A list of credentials.
     */
    public void setCredentials(final List<CommonCredential> credentials) {
        this.credentials = credentials;
    }

    /**
     * Checks if the secrets contained in this DTO need to be merged with
     * existing credentials.
     * <p>
     * Otherwise the existing credentials can simply be replaced with the data contained
     * in this DTO.
     *
     * @return {@code true} if any of the secrets has an ID property and thus refers to
     *         an existing secret.
     */
    @JsonIgnore
    public boolean requiresMerging() {
        return requiresMerging;
    }

    /**
     * Assigns a unique ID to each of the credentials' secrets that do not have one already.
     */
    public void createMissingSecretIds() {
        Optional.ofNullable(credentials)
            .ifPresent(creds -> creds.stream().forEach(CommonCredential::createMissingSecretIds));
    }

    /**
     * Merges the secrets of the given credential DTO with that of the current one.
     *
     * @param otherCredentialsDto The credential DTO to be merged.
     * @return  a reference to this for fluent use.
     * @throws NullPointerException if the given credentials DTO is {@code null}.
     */
    @JsonIgnore
    public CredentialsDto merge(final CredentialsDto otherCredentialsDto) {
        Objects.requireNonNull(otherCredentialsDto);

        Optional.ofNullable(otherCredentialsDto.getCredentials())
                .ifPresent(credentialsToMerge -> this.credentials
                        .forEach(credential -> findCredentialByIdAndType(credential.getAuthId(), credential.getType(),
                                credentialsToMerge)
                                        .ifPresent(credential::merge)));

        return this;
    }

    @JsonIgnore
    private static Optional<CommonCredential> findCredentialByIdAndType(
            final String authId,
            final String authType,
            final List<CommonCredential> credentials) {

        return credentials.stream()
                .filter(credential -> authId.equals(credential.getAuthId()) && authType.equals(credential.getType()))
                .findFirst();
    }
}
