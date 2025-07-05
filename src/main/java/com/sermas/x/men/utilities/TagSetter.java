package com.sermas.x.men.utilities;

import com.sermas.x.men.model.Flags;
import com.sermas.x.men.model.Mutations;
import com.sermas.x.men.model.ParametersBundle;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Set the tags for the parameters bundle for Add and Replace Mutations.
 */
@Component
public class TagSetter {

  /**
   * Set the tags for the parameters bundle.
   *
   * @param parametersBundle The parameters bundle.
   * @param mutationSet The set of mutations.
   * @return The parameters bundle with the tags set.
   */
  public ParametersBundle setTags(ParametersBundle parametersBundle, Set<Mutations> mutationSet) {
    Flags flags = parametersBundle.getFlags();

    // Sets tags for parameters bundle depending on the mutation set (Add, Replace, Combine Add
    // Replace, Combine Add Replace Only).
    for (Mutations mutation : mutationSet) {
      switch (mutation) {
        case Mutations.REPLACE_TYPE:
          flags.setReplaceType(true);
          parametersBundle.setFlags(flags);
          break;
        case Mutations.REPLACE_SUB_MESSAGES:
          flags.setReplaceSubmessages(true);
          parametersBundle.setFlags(flags);
          break;
        case ADD:
          if ((mutationSet.contains(Mutations.REPLACE_SUB_MESSAGES)
                  || mutationSet.contains(Mutations.REPLACE_TYPE))
              && mutationSet.contains(Mutations.COMBINE_ADD_REPLACE)) {
            flags.setCombineAddReplace(true);
            flags.setAdd(true);
            parametersBundle.setFlags(flags);
          }
          if ((mutationSet.contains(Mutations.REPLACE_SUB_MESSAGES)
                  || mutationSet.contains(Mutations.REPLACE_TYPE))
              && mutationSet.contains(Mutations.COMBINE_ADD_REPLACE_ONLY)) {
            flags.setCombineAddReplaceOnly(true);
            flags.setAdd(true);
            parametersBundle.setFlags(flags);
          }
          break;
        default:
      }
    }
    return parametersBundle;
  }
}
