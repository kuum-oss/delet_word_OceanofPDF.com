package org.example;

public enum CropMode {
    SKIP,           // Пропустить (не менять)
    FIT_WIDTH,      // Растянуть по ширине (стандарт для манги)
    FIT_HEIGHT,     // Растянуть по высоте
    STRETCH,        // Растянуть на весь экран (искажение)
    CENTER_ONLY     // Не менять масштаб, просто центрировать
}