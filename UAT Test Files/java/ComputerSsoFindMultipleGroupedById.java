package com.code42.computer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.google.common.base.Function;

public class ComputerSsoFindMultipleGroupedById<T> extends AbstractCmd<Map<T, Collection<ComputerSso>>> {

	private final Set<T> ids;
	private final Function<T, Set<Long>> mappingFunction;

	public ComputerSsoFindMultipleGroupedById(Collection<T> ids, Function<T, Set<Long>> mappingFunction) {
		this(new HashSet<T>(ids), mappingFunction);
	}

	public ComputerSsoFindMultipleGroupedById(Set<T> ids, Function<T, Set<Long>> mappingFunction) {
		this.ids = ids;
		this.mappingFunction = mappingFunction;
	}

	@Override
	public Map<T, Collection<ComputerSso>> exec(CoreSession session) throws CommandException {
		Map<T, Collection<ComputerSso>> rv = new HashMap<T, Collection<ComputerSso>>(this.ids.size());

		Map<T, Set<Long>> idToGuidMap = new HashMap<T, Set<Long>>(this.ids.size());
		Set<Long> allGuids = new HashSet<Long>();
		for (T id : this.ids) {
			Set<Long> guids = this.mappingFunction.apply(id);
			idToGuidMap.put(id, guids);
			allGuids.addAll(guids);
		}

		Map<Long, ComputerSso> allComputers = this.run(new ComputerSsoFindMultipleByGuidCmd(allGuids), session);

		for (T id : idToGuidMap.keySet()) {
			Set<Long> guids = idToGuidMap.get(id);
			List<ComputerSso> computers = new ArrayList<ComputerSso>(guids.size());
			for (long guid : guids) {
				computers.add(allComputers.get(guid));
			}
			rv.put(id, computers);
		}

		return rv;
	}
}
