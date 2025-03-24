package com.github.bric3.excalidraw.mpi.events

import kotlin.reflect.KProperty

// TODO Remove
internal object PropertyNameDelegate {
  operator fun getValue(thisRef: Any?, property: KProperty<*>) = property.name
}
