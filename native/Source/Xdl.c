#include <xdl.c>

void *FindArtPrefix(void *Handle, const char *Prefix)
{
    if (!Handle || !Prefix)
        return NULL;
    xdl_t *Library = (xdl_t *)Handle;
    xdl_ensure_symtab_loaded(Library);
    if (!Library->symtab)
        return NULL;
    size_t Length = strlen(Prefix);
    for (size_t Index = 0; Index < Library->symtab_cnt; Index++)
    {
        ElfW(Sym) *Symbol = Library->symtab + Index;
        if (!XDL_SYMTAB_IS_EXPORT_SYM(Symbol->st_shndx) || Symbol->st_name >= Library->strtab_sz || Length > Library->strtab_sz - Symbol->st_name)
            continue;
        if (strncmp(Library->strtab + Symbol->st_name, Prefix, Length) == 0)
            return xdl_resolve_symbol_address(Library, Symbol, NULL);
    }
    return NULL;
}
