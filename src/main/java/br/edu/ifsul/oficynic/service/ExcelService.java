package br.edu.ifsul.oficynic.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class ExcelService {

    // Dinheiro fica como numero para somar no Excel
    public byte[] gerar(
            String titulo,
            String filtros,
            List<String> colunas,
            List<List<Object>> linhas,
            List<List<Object>> resumo,
            String observacao) {

        try (Workbook planilha = new XSSFWorkbook(); var saida = new ByteArrayOutputStream()) {
            Sheet aba = planilha.createSheet("Relatório");
            Font negrito = planilha.createFont();
            negrito.setBold(true);
            CellStyle estiloTitulo = planilha.createCellStyle();
            estiloTitulo.setFont(negrito);
            CellStyle estiloCabecalho = planilha.createCellStyle();
            estiloCabecalho.setFont(negrito);
            estiloCabecalho.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            estiloCabecalho.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle estiloDinheiro = planilha.createCellStyle();
            estiloDinheiro.setDataFormat(planilha.createDataFormat().getFormat("\"R$\" #,##0.00"));

            int numeroLinha = 0;
            aba.createRow(numeroLinha++).createCell(0).setCellValue("OFICYNIC — " + titulo);
            aba.getRow(0).getCell(0).setCellStyle(estiloTitulo);
            aba.createRow(numeroLinha++).createCell(0).setCellValue(filtros);
            numeroLinha++;

            Row cabecalho = aba.createRow(numeroLinha++);
            for (int i = 0; i < colunas.size(); i++) {
                Cell celula = cabecalho.createCell(i);
                celula.setCellValue(colunas.get(i));
                celula.setCellStyle(estiloCabecalho);
            }
            for (List<Object> valores : linhas) {
                preencherLinha(aba.createRow(numeroLinha++), valores, estiloDinheiro);
            }
            if (resumo != null && !resumo.isEmpty()) {
                numeroLinha++;
                Row tituloResumo = aba.createRow(numeroLinha++);
                tituloResumo.createCell(0).setCellValue("Resumo");
                tituloResumo.getCell(0).setCellStyle(estiloTitulo);

                for (List<Object> valores : resumo) {
                    preencherLinha(aba.createRow(numeroLinha++), valores, estiloDinheiro);
                }
            }
            if (observacao != null) {
                numeroLinha++;
                aba.createRow(numeroLinha).createCell(0).setCellValue(observacao);
            }

            for (int i = 0; i < colunas.size(); i++) {
                aba.autoSizeColumn(i);
            }

            planilha.write(saida);

            return saida.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível gerar a planilha.", e);
        }
    }

    private void preencherLinha(Row linha, List<Object> valores, CellStyle estiloDinheiro) {
        for (int i = 0; i < valores.size(); i++) {
            Object valor = valores.get(i);
            Cell celula = linha.createCell(i);
            if (valor instanceof BigDecimal dinheiro) {
                celula.setCellValue(dinheiro.doubleValue());
                celula.setCellStyle(estiloDinheiro);
            } else if (valor instanceof Number numero) {
                celula.setCellValue(numero.doubleValue());
            } else if (valor instanceof LocalDate) {
                celula.setCellValue(PdfService.formatar(valor));
            } else {
                celula.setCellValue(valor == null ? "" : String.valueOf(valor));
            }
        }
    }
}
