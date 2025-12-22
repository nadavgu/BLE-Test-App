package com.nadavgu.bletestapp.server.spec

import java.util.UUID

data class BleServiceSpec(val uuid: UUID, val characteristicUuids: List<UUID>)
