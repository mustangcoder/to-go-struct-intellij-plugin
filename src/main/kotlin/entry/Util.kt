package entry

import TextCopyForm
import com.google.common.base.CaseFormat

fun String.underscoreToCamel(): String {
    val names = this.split("_")
    val sb = StringBuilder()
    for (n in names) {
        sb.append(n.firstToUpper())
    }
    return sb.toString()
}

fun String.fmtName(): String {
    var name = this.clearName().firstToUpper()
    if (name.contains("_")) name = name.underscoreToCamel()
    return name.replace("Id".toRegex(), "ID")
}

fun String.clearName() =
    this.replace("`".toRegex(), "").replace("'".toRegex(), "").replace("\"".toRegex(), "")


fun String.firstToUpper(): String {
    if (this.isEmpty()) return ""
    val ch = this.toCharArray()
    ch[0] = ch[0].toUpperCase()
    return String(ch)
}

// tpl should: `json:"%s" bson:"%s" etc...`
fun String.makeTags(tpl: String): String {
    if (tpl.isEmpty()) {
        return ""
    }

    var tagList = tpl.split(";")
    var result = ""
    for (tag in tagList) {
        var realVal = ""
        if (tag.contains(TextCopyForm.CAMEL_CASE)) {
            // 转驼峰
            if (this.matches(Regex(".*[A-Z].*")) && !this.contains("_")) {
                realVal = CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(this).toString()
                realVal = CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL).convert(realVal).toString()
            } else if (!this.matches(Regex(".*[A-Z].*")) && this.contains("_")) {
                realVal = CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL).convert(this).toString()
            } else {
                realVal = this
            }
        } else if (tag.contains(TextCopyForm.UNDER_SCORE)) {
            // 转下划线
            if (this.matches(Regex(".*[A-Z].*")) && !this.contains("_")) {
                realVal = CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(this).toString()
            } else if (!this.matches(Regex(".*[A-Z].*")) && this.contains("_")) {
                realVal = CaseFormat.LOWER_UNDERSCORE.converterTo(CaseFormat.LOWER_CAMEL).convert(this).toString()
                realVal = CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.LOWER_UNDERSCORE).convert(realVal).toString()
            } else {
                realVal = this
            }
        } else {
            realVal = this
        }
        result = result + tag.replace("%s", realVal)
            .replace(" " + TextCopyForm.CAMEL_CASE, "")
            .replace(" " + TextCopyForm.UNDER_SCORE, "") +
                " "
    }

    return "    `" + result + "`"
}

fun convertToCamelCase(columnName: String): String {
    var itemList = columnName.toLowerCase().split("_")
    var newColumnName = ""
    for (i in itemList.indices) {
        if (i == 0) {
            newColumnName = newColumnName + itemList[i]
            continue
        }
        newColumnName = newColumnName + itemList[i].get(0).toUpperCase() + itemList[i].substring(1)
    }
    return newColumnName
}

fun String.makeDaoFunc(): String {
    val dao = this + "Dao"
    return """
type $dao struct {
    sourceDB  *gorm.DB
    replicaDB []*gorm.DB
    m         *$this
}

func New$dao(ctx context.Context, dbs ...*gorm.DB) *$dao {
    dao := new($dao)
    switch len(dbs) {
    case 0:
        panic("database connection required")
    case 1:
        dao.sourceDB = dbs[0]
        dao.replicaDB = []*gorm.DB{dbs[0]}
    default:
        dao.sourceDB = dbs[0]
        dao.replicaDB = dbs[1:]
    }
    return dao
}
    """.trimIndent()
}

fun String.makeCreateFunc(): String {
    val dao = this + "Dao"
    return """
func (d *$dao) Create(ctx context.Context, obj *$this) error {
	err := d.sourceDB.Model(d.m).Create(&obj).Error
	if err != nil {
		return fmt.Errorf("$dao: %w", err)
	}
	return nil
}"""
}


fun String.makeUpdateFunc(): String {
    val dao = this + "Dao"
    return """
func (d *$dao) Update(ctx context.Context, where string, update map[string]interface{}, args ...interface{}) error {
    err := d.sourceDB.Model(d.m).Where(where, args...).
        Updates(update).Error
    if err != nil {
        return fmt.Errorf("$dao:Update where=%s: %w", where, err)
    }
    return nil
}
    """.trimIndent()
}

fun String.makeGetFunc(): String {
    val dao = this + "Dao"
    return """
func (d *$dao) Get(ctx context.Context, fields, where string) (*$this, error) {
    items, err := d.List(ctx, fields, where, 0, 1)
    if err != nil {
        return nil, fmt.Errorf("$dao: Get where=%s: %w", where, err)
    }
    if len(items) == 0 {
        return nil, gorm.ErrRecordNotFound
    }
    return &items[0], nil
}
    """.trimIndent()
}

fun String.makeListFunc(): String {
    val dao = this + "Dao"
    return """
func (d *$dao) List(ctx context.Context, fields, where string, offset, limit interface{}) ([]$this, error) {
    var results []$this
    err := d.replicaDB[rand.Intn(len(d.replicaDB))].Model(d.m).
        Select(fields).Where(where).Offset(offset).Limit(limit).Find(&results).Error
    if err != nil {
        return nil, fmt.Errorf("$dao: List where=%s: %w", where, err)
    }
    return results, nil
}
    """.trimIndent()
}

fun String.makeDeleteFunc(): String {
    val dao = this + "Dao"
    return """
func (d *$dao) Delete(ctx context.Context, where string, args ...interface{}) error {
    if len(where) == 0 {
        return gorm.ErrInvalidSQL
    }
    if err := d.sourceDB.Where(where, args...).Delete(d.m).Error; err != nil {
        return fmt.Errorf("$dao: Delete where=%s: %w", where, err)
    }
    return nil
}
    """.trimIndent()
}
