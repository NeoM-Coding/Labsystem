package xyz.jasenon.lab.common.command.seq;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SeqRuleLoaderTests {

    @AfterEach
    void reloadDefaultRules() {
        SeqGeneratorManager.clear();
        SeqRuleLoader.loadDefault();
    }

    @Test
    void loadsRulesAndRegistersGenerators() {
        SeqGeneratorManager.clear();

        List<SeqRule> rules = SeqRuleLoader.load(new StringReader("""
                seq AccessReq {
                  address = 0
                  function_code = 1,2
                }

                seq AccessResp {
                  address = 0
                  function_code = 1,2
                }
                """));

        assertEquals(2, rules.size());
        assertEquals(
                "address=01|function_code=0A02",
                SeqGeneratorManager.get(SeqType.AccessReq).generate(new byte[]{1, 10, 2, (byte) 255, 0, 0, 13})
        );
        assertEquals(
                "address=01|function_code=0A02",
                SeqGeneratorManager.get(SeqType.AccessResp).generate(new byte[]{1, 10, 2, (byte) 255, 13})
        );
    }

    @Test
    void generatorRejectsPayloadShorterThanConfiguredIndexes() {
        SeqGenerator generator = new RuleBasedSeqGenerator(new SeqRule(
                SeqType.AccessReq,
                List.of(new SeqFieldRule("function_code", List.of(1, 2)))
        ));

        assertThrows(IllegalArgumentException.class, () -> generator.generate(new byte[]{1, 10}));
    }

    @Test
    void rejectsDuplicateFields() {
        assertThrows(IllegalArgumentException.class, () -> SeqRuleLoader.load(new StringReader("""
                seq AccessReq {
                  address = 0
                  address = 1
                }
                """)));
    }

    @Test
    void rejectsUnknownSeqType() {
        assertThrows(IllegalArgumentException.class, () -> SeqRuleLoader.load(new StringReader("""
                seq MissingType {
                  address = 0
                }
                """)));
    }

    @Test
    void rejectsNegativeIndex() {
        assertThrows(IllegalArgumentException.class, () -> SeqRuleLoader.load(new StringReader("""
                seq AccessReq {
                  address = -1
                }
                """)));
    }
}
