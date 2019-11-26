package com.googlecode.pngtastic.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the available PNG filter types.
 * @see <a href="http://www.w3.org/TR/PNG/#9Filters">Filtering</a>
 *
 * @author rayvanderborght
 */
public enum PngFilterType {
	ADAPTIVE(-1),	// NOTE: not a real filter type
	NONE(0),
	SUB(1),
	UP(2),
	AVERAGE(3),
	PAETH(4);

	private static final List<PngFilterType> filterTypes = new ArrayList<>();

	static {
		filterTypes.add(NONE);
		filterTypes.add(SUB);
		filterTypes.add(UP);
		filterTypes.add(AVERAGE);
		filterTypes.add(PAETH);
	}

	public static void removeFilter(PngFilterType type){
		synchronized (filterTypes) {
			filterTypes.remove(type);
		}
	}

	public static void addFilter(PngFilterType type){
		synchronized (filterTypes) {
			if (!filterTypes.contains(type)) {
				filterTypes.add(type);
			}
		}
	}

	/** */
	private byte value;
	public byte getValue() { return this.value; }

	/* */
	private PngFilterType() { }

	/* */
	private PngFilterType(int i) {
		this.value = (byte) i;
	}

	/** */
	public static PngFilterType forValue(byte value) {
		for (PngFilterType type : PngFilterType.values()) {
			if (type.getValue() == value)
				return type;
		}
		return NONE;
	}

	/** */
	public static PngFilterType[] standardValues() {
		synchronized (filterTypes) {
			return filterTypes.toArray(new PngFilterType[0]);
		}
	}
}
