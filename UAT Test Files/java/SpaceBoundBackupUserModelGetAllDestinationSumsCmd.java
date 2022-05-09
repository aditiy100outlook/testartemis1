package com.code42.stats;

import java.util.Map;

import com.code42.core.CommandException;
import com.code42.core.auth.impl.CoreSession;
import com.code42.core.impl.AbstractCmd;
import com.code42.core.relation.IRelationService;
import com.code42.utils.Pair;
import com.google.inject.Inject;

/**
 * Concrete implementation of {@link AbstractSpaceBoundModelCmd} which returns per-destination sums only. This command
 * builds on the foundation provided by {@link SpaceBoundModelUtils} by providing the business logic of correctly using
 * IRelationService to translate from spaces to GUIDs. It also enforces usage of the correct combiner. <br>
 * <br>
 * Note that this command assumes that your IRelationService instance has full visibility to all space-to-destination
 * mappings. If that's not the case (i.e. if you're passing in a set of mappings from another source) you should
 * probably use the static methods in {@link SpaceBoundModelUtils} directly. <br>
 * <br>
 * Wow, is the name of this command ugly.
 * 
 * @author bmcguire
 */
public class SpaceBoundBackupUserModelGetAllDestinationSumsCmd extends AbstractCmd<Map<Long, AggregateBackupStats>> {

	/* ================= Dependencies ================= */
	private IRelationService relation;

	/* ================= DI injection points ================= */
	@Inject
	public void setRelation(IRelationService relation) {
		this.relation = relation;
	}

	private Map<Pair<Long, Long>, AggregateBackupStats> model;

	public SpaceBoundBackupUserModelGetAllDestinationSumsCmd(Map<Pair<Long, Long>, AggregateBackupStats> model) {

		if (model == null) {
			throw new IllegalArgumentException("Input model cannot be null");
		}

		this.model = model;
	}

	@Override
	public Map<Long, AggregateBackupStats> exec(CoreSession session) throws CommandException {

		return SpaceBoundModelUtils.sumUserSpaceModel(this.model, this.relation.getSpaceDestinationMappings());
	}
}
