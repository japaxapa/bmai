package com.example.bms.shared;

/**
 * The body of the deactivation convention every collection answers with
 * (api-design: {@code PUT /{resource}/{id}/status} with {@code {active}}).
 * One record so the shape cannot drift resource to resource — categories
 * and products carry it today; users, suppliers and customers (#7/#8) read
 * it next, the way they read {@link PageSize}.
 */
public record StatusRequest(boolean active) {
}
